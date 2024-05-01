package org.vatplanner.commons.vcs.jgit_adapter;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vatplanner.commons.vcs.Revision;
import org.vatplanner.commons.vcs.RevisionVisitor;
import org.vatplanner.commons.vcs.VCSRepositoryConfiguration;
import org.vatplanner.commons.vcs.VCSRepositoryControl;
import org.vatplanner.commons.vcs.VCSRepositoryControlFactory;

import com.google.auto.service.AutoService;

/**
 * Implements control over Git&reg; repositories through Eclipse JGit&trade;.
 *
 * <h3>Acknowledgements</h3>
 * <p>
 * <a href="https://www.eclipse.org/" target="_blank">Eclipse</a> and
 * <a href="https://www.eclipse.org/jgit/" target="_blank">Eclipse JGit</a> are
 * trademarks of <a href="https://www.eclipse.org/" target="_blank">Eclipse Foundation, Inc.</a>
 * </p>
 *
 * <p>
 * <a href="https://git-scm.com/" target="_blank">Git</a> and the Git logo are either registered trademarks or
 * trademarks of <a href="https://sfconservancy.org/" target="_blank">Software Freedom Conservancy, Inc.</a>, corporate
 * home of the Git Project, in the United States and/or other countries.
 * </p>
 *
 * <p>
 * This project (VATPlanner) has no affiliation with the mentioned trademark holders and shall not be confused with any
 * of these projects and/or organizations. JGit and Git are only being mentioned to describe this project's dependencies
 * and to acknowledge the trademarks as requested by the respective parties.
 * </p>
 */
public class JGitVCSRepositoryControl extends VCSRepositoryControl {
    private static final Logger LOGGER = LoggerFactory.getLogger(JGitVCSRepositoryControl.class);

    private static final String SYSTEM = "git";
    public static final String DEFAULT_REMOTE_NAME = "_" + JGitVCSRepositoryControl.class.getSimpleName();

    private final URIish upstreamLocation;
    private final Repository repo;
    private final Git git;
    private final String remoteName;
    private final CredentialsProvider credentialsProvider;
    private final String branchName;

    private JGitVCSRepositoryControl(VCSRepositoryConfiguration config) {
        if (!SYSTEM.equals(config.getSystem())) {
            throw new IllegalArgumentException("Wrong system: '" + config.getSystem() + "'");
        }

        remoteName = DEFAULT_REMOTE_NAME; // TODO: make configurable
        boolean isBareRepository = true; // TODO: make configurable
        branchName = config.getBranch().orElse(null);

        try {
            upstreamLocation = toUriish(config.getUrl()).orElse(null);

            File storage = config.getStorage();
            if (!storage.exists() && !storage.mkdirs()) {
                throw new ConfigurationFailed("Unable to create directory: " + storage);
            }

            String username = config.getUsername().orElse(null);
            char[] password = config.getPassword().orElse(new char[0]);
            if ((upstreamLocation == null) || (username == null)) {
                credentialsProvider = null;
            } else {
                credentialsProvider = new UsernamePasswordProvider(upstreamLocation, username, password);
            }

            RepositoryBuilder repoBuilder = new RepositoryBuilder().setGitDir(storage);
            if (isBareRepository) {
                repoBuilder.setBare();
            }
            repo = repoBuilder.build();

            if (!repo.getObjectDatabase().exists()) {
                String[] list = storage.list();
                if (list == null) {
                    throw new ConfigurationFailed("Storage directory does not appear to contain a Git repository, contents could not be listed: " + storage);
                } else if (list.length != 0) {
                    throw new ConfigurationFailed("Storage directory is not empty but does not contain a Git repository either: " + storage);
                }

                repo.create(isBareRepository);
            }

            git = new Git(repo);
        } catch (IOException ex) {
            throw new ConfigurationFailed("Failed to configure VCS control", ex);
        }
    }

    private Optional<URIish> toUriish(Optional<String> url) {
        if (!url.isPresent()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new URIish(url.get()));
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("invalid URI: '" + url.get() + "'", ex);
        }
    }

    @Override
    public void syncDown(Duration timeout) {
        if (upstreamLocation == null) {
            LOGGER.warn("no upstream location has been configured, repository only exists locally");
            return;
        }

        try {
            ensureRemoteUri(remoteName, upstreamLocation);

            LOGGER.info("fetching from {}", upstreamLocation);
            git.fetch()
               .setRemote(remoteName)
               .setCredentialsProvider(credentialsProvider)
               .setProgressMonitor(new LoggingProgressMonitor(LOGGER, "[fetch " + upstreamLocation + "] "))
               .setTimeout(Math.max(1, (int) timeout.getSeconds()))
               .call();
            LOGGER.info("fetch from {} completed", upstreamLocation);
        } catch (GitAPIException ex) {
            throw new OperationFailed("failed to fetch from remote location (sync down) at " + upstreamLocation, ex);
        }
    }

    private ObjectId findBranchObjectId(String expectedName) {
        String expectedRefName = "refs/" + expectedName; // FIXME: may not be correct for local branches

        List<ObjectId> matched;
        try {
            matched = git.branchList()
                         .setListMode(ListBranchCommand.ListMode.ALL)
                         .call()
                         .stream()
                         .filter(ref -> expectedRefName.equals(ref.getName()))
                         .map(Ref::getObjectId)
                         .collect(Collectors.toList());
        } catch (GitAPIException ex) {
            throw new OperationFailed("failed to list branches of repository at " + repo.getDirectory(), ex);
        }

        if (matched.isEmpty()) {
            throw new IllegalArgumentException("branch '" + expectedName + "' not found in repository at " + repo.getDirectory());
        } else if (matched.size() != 1) {
            throw new IllegalArgumentException("branch '" + expectedName + "' is ambiguous in repository at " + repo.getDirectory());
        }

        return matched.iterator().next();
    }

    @Override
    public void walkLog(RevisionVisitor visitor) {
        LogCommand command = git.log();

        ObjectId startRef = null;
        String wantedBranchName = (branchName != null) ? branchName : "";
        if (remoteName != null) {
            if (branchName == null) {
                throw new OperationFailed("branches of remotes/bare repositories can only be located if a branch name is set");
            }
            wantedBranchName = "remotes/" + remoteName + "/" + wantedBranchName;
        }
        if (!wantedBranchName.isEmpty()) {
            startRef = findBranchObjectId(wantedBranchName);
        }

        try {
            if (startRef != null) {
                command.add(startRef);
            }
        } catch (MissingObjectException | IncorrectObjectTypeException ex) {
            throw new OperationFailed(
                "invalid branch: '" + branchName + "' resolved to object ID " + startRef + " failed lookup in repository at " + repo.getDirectory(),
                ex
            );
        }

        Iterable<RevCommit> commits;
        try {
            commits = command.call();
        } catch (GitAPIException ex) {
            throw new OperationFailed(
                "failed to retrieve log (walk history) of repository at " + repo.getDirectory() + " "
                    + (wantedBranchName.isEmpty() ? "(no branch)" : "(branch " + wantedBranchName + " => " + startRef + ")"),
                ex
            );
        }

        for (RevCommit commit : commits) {
            Revision.Builder builder = Revision.builder()
                                               .setId(commit.getName())
                                               .setParentIds(
                                                   Arrays.stream(commit.getParents())
                                                         .map(RevCommit::getName)
                                                         .collect(Collectors.toList())
                                               )
                                               .setTimestamp(Instant.ofEpochSecond(commit.getCommitTime()));

            String message = commit.getFullMessage();
            if (message != null) {
                builder.setMessage(message.trim());
            }

            RevisionVisitor.Action action = visitor.visit(builder.build());
            if (action == RevisionVisitor.Action.ABORT_WALK) {
                break;
            } else if (action != RevisionVisitor.Action.CONTINUE_WALK) {
                throw new IllegalArgumentException("Unhandled walker action: " + action);
            }
        }
    }

    private void ensureRemoteUri(String remoteName, URIish uri) throws GitAPIException {
        boolean remoteExists = git.remoteList()
                                  .call()
                                  .stream()
                                  .map(RemoteConfig::getName)
                                  .anyMatch(remoteName::equals);

        if (remoteExists) {
            git.remoteSetUrl()
               .setRemoteName(remoteName)
               .setRemoteUri(uri)
               .call();
        } else {
            git.remoteAdd()
               .setName(remoteName)
               .setUri(uri)
               .call();
        }
    }

    @Override
    public void close() throws IOException {
        git.close();
    }

    private static class UsernamePasswordProvider extends CredentialsProvider {
        private final URIish location;
        private final String username;
        private final char[] password;

        UsernamePasswordProvider(URIish location, String username, char[] password) {
            super();
            this.location = location;
            this.username = username;
            this.password = password;
        }

        @Override
        public boolean isInteractive() {
            return false;
        }

        @Override
        public boolean supports(CredentialItem... items) {
            LOGGER.debug("backend checks credential support");
            return supportsInternally(items);
        }

        @Override
        public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {
            LOGGER.debug("backend wants credentials for {}", uri);
            if (!Objects.equals(uri, location)) {
                LOGGER.warn("backend requested credentials for unexpected location {}", uri);
                return false;
            }

            if (!supportsInternally(items)) {
                LOGGER.warn("backend requested unsupported authentication method for {}: {}", uri, items);
                return false;
            }

            for (CredentialItem item : items) {
                if (item instanceof CredentialItem.Username) {
                    ((CredentialItem.Username) item).setValue(username);
                } else if (item instanceof CredentialItem.Password) {
                    ((CredentialItem.Password) item).setValue(password);
                }
            }

            return true;
        }

        private boolean supportsInternally(CredentialItem[] items) {
            boolean wantsUsername = false;
            boolean wantsPassword = false;
            boolean wantsOther = false;

            for (CredentialItem item : items) {
                if (item instanceof CredentialItem.Username) {
                    LOGGER.debug("backend wants username: {}", item);
                    wantsUsername = true;
                } else if (item instanceof CredentialItem.Password) {
                    LOGGER.debug("backend wants password: {}", item);
                    wantsPassword = true;
                } else {
                    LOGGER.debug("backend wants unsupported credential type: {}", item);
                    wantsOther = true;
                }
            }

            if (!(wantsUsername && wantsPassword)) {
                LOGGER.debug("only username and password are implemented for authentication, backend asked for something else");
                return false;
            }

            if (wantsOther) {
                LOGGER.debug("something else was requested in addition to username and password, blocking");
                return false;
            }

            return true;
        }
    }

    @AutoService(VCSRepositoryControlFactory.class)
    public static class Factory implements VCSRepositoryControlFactory {
        @Override
        public String getSupportedSystem() {
            return SYSTEM;
        }

        @Override
        public VCSRepositoryControl createFromConfiguration(VCSRepositoryConfiguration config) {
            return new JGitVCSRepositoryControl(config);
        }
    }
}
