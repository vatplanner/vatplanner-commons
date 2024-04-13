package org.vatplanner.commons.fileaccess.jgit_adapter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vatplanner.commons.fileaccess.AccessPath;
import org.vatplanner.commons.fileaccess.FileAccessProvider;
import org.vatplanner.commons.fileaccess.FileHolder;
import org.vatplanner.commons.fileaccess.FileInfo;

/**
 * Provides access to files in Git&reg; repositories through Eclipse JGit&trade;.
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
public class JGitFileAccessProvider implements FileAccessProvider.RandomAccess {
    private static final Logger LOGGER = LoggerFactory.getLogger(JGitFileAccessProvider.class);

    private final Repository repository;
    private final RevTree revTree;

    private static final Collection<Predicate<File>> META_DATA_DIRECTORY_REQUIREMENTS = Arrays.asList(
        item -> "index".equals(item.getName()) && item.isFile(),
        item -> "HEAD".equals(item.getName()) && item.isFile(),
        item -> "logs".equals(item.getName()) && item.isDirectory(),
        item -> "objects".equals(item.getName()) && item.isDirectory(),
        item -> "refs".equals(item.getName()) && item.isDirectory()
    );

    private interface TreeWalkVisitor {
        void visit(FileInfo info, ObjectLoader objectLoader) throws IOException;
    }

    /**
     * Instantiates a new {@link FileAccessProvider} for the repository at given location.
     * <p>
     * The repository should not be modified while this instance is being used.
     * </p>
     *
     * @param repositoryPath path to non-empty repository; must be root or metadata directory
     */
    public JGitFileAccessProvider(File repositoryPath) {
        this(findRepository(repositoryPath));
    }

    /**
     * Instantiates a new {@link FileAccessProvider} for the given {@link Repository}.
     * <p>
     * If the {@link Repository} is accessed concurrently outside this implementation, all access to it must be
     * synchronized over the given instance.
     * </p>
     *
     * @param repository repository to access
     */
    public JGitFileAccessProvider(Repository repository) {
        this.repository = repository;

        synchronized (this.repository) {
            boolean hasRefs;
            try {
                hasRefs = !repository.getRefDatabase().getRefs().isEmpty();
            } catch (IOException ex) {
                throw new RepositoryAccessFailed("Repository at " + repository.getDirectory() + " could not be accessed", ex);
            }

            if (!hasRefs) {
                throw new RepositoryAccessFailed("Repository at " + repository.getDirectory() + " is empty (check path; this also happens if no repository is present at the specified location)");
            }
        }

        this.revTree = findTreeForCommit("HEAD");
    }

    private JGitFileAccessProvider(Repository repository, RevTree revTree) {
        this.repository = repository;
        this.revTree = revTree;
    }

    /**
     * Returns a view of the repository at time of the commit identified by given ref string. A ref string can be the
     * exact commit hash, a branch name, a tag or anything else that can be resolved to a commit by the underlying
     * implementation.
     *
     * @param refString ref string identifying the wanted commit
     * @return a view of the repository at time of the specified commit
     */
    public JGitFileAccessProvider atCommit(String refString) {
        return new JGitFileAccessProvider(repository, findTreeForCommit(refString));
    }

    @Override
    public Stream<FileHolder> streamFiles() {
        Stream.Builder<FileHolder> streamBuilder = Stream.builder();
        forEach((fileInfo, objectLoader) -> streamBuilder.accept(new FileHolder(fileInfo, objectLoader.getBytes())));
        return streamBuilder.build();
    }

    @Override
    public Stream<FileHolder> streamFiles(Predicate<FileInfo> filter) {
        Stream.Builder<FileHolder> streamBuilder = Stream.builder();
        forEach(filter, streamBuilder);
        return streamBuilder.build();
    }

    @Override
    public Optional<FileHolder> getFile(AccessPath path) {
        String treePath = String.join("/", path.getPathSegments());

        synchronized (repository) {
            try {
                ObjectId objectId;
                try (TreeWalk treeWalk = TreeWalk.forPath(repository, treePath, revTree)) {
                    if (treeWalk == null) {
                        return Optional.empty();
                    }

                    objectId = treeWalk.getObjectId(0);
                }

                ObjectLoader objectLoader = repository.open(objectId);

                return Optional.of(new FileHolder(
                    FileInfo.builder()
                            .setSize(objectLoader.getSize())
                            .setPath(path)
                            .build(),
                    objectLoader.getBytes()
                ));
            } catch (IOException ex) {
                throw new RepositoryAccessFailed("Failed to look up " + path + " (" + treePath + ")", ex);
            }
        }
    }

    @Override
    public Set<FileInfo> listFiles() {
        Set<FileInfo> out = new HashSet<>();
        forEach((fileInfo, objectLoader) -> out.add(fileInfo));
        return out;
    }

    @Override
    public void forEach(Predicate<FileInfo> filter, Consumer<FileHolder> consumer) {
        forEach((fileInfo, objectLoader) -> {
            if (filter.test(fileInfo)) {
                consumer.accept(new FileHolder(fileInfo, objectLoader.getBytes()));
            }
        });
    }

    private void forEach(TreeWalkVisitor visitor) {
        synchronized (repository) {
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(revTree);
                treeWalk.setRecursive(true);

                while (treeWalk.next()) {
                    int fileModeBits = treeWalk.getFileMode().getBits();
                    boolean isFile = ((fileModeBits & FileMode.TYPE_FILE) != 0);
                    if (!isFile) {
                        continue;
                    }

                    String[] pathSegments = treeWalk.getPathString().split("/");
                    AccessPath accessPath = new AccessPath(Arrays.asList(pathSegments), AccessPath.Type.FILE);

                    ObjectLoader objectLoader = repository.open(treeWalk.getObjectId(0));

                    FileInfo fileInfo = FileInfo.builder()
                                                .setPath(accessPath)
                                                .setSize(objectLoader.getSize())
                                                .build();

                    visitor.visit(fileInfo, objectLoader);
                }
            } catch (IOException ex) {
                throw new RepositoryAccessFailed("Failed to walk tree on repository " + repository.getDirectory() + " (ObjectId: " + revTree.toObjectId() + ")", ex);
            }
        }
    }

    private RevTree findTreeForCommit(String refString) {
        synchronized (repository) {
            Ref ref;
            try {
                ref = repository.findRef(refString);
            } catch (IOException ex) {
                throw new RepositoryAccessFailed("Ref string \"" + refString + "\" search failed on repository " + repository.getDirectory(), ex);
            }

            // findRef succeeds for "meta refs" like HEAD but returns null for commit hashes,
            // on the other hand "meta refs" cannot be found by just parsing them to ObjectIds
            ObjectId objectId = (ref != null) ? ref.getObjectId() : ObjectId.fromString(refString);

            try (RevWalk revWalk = new RevWalk(repository)) {
                return revWalk.parseCommit(objectId).getTree();
            } catch (IOException ex) {
                throw new RepositoryAccessFailed("No commit found for ref string \"" + refString + "\" on repository " + repository.getDirectory() + " (ObjectId: " + objectId + ")", ex);
            }
        }
    }

    private static Repository findRepository(File directory) {
        if (!directory.exists()) {
            throw new RepositoryAccessFailed("Not found: " + directory.getAbsolutePath());
        }

        if (!directory.isDirectory()) {
            throw new RepositoryAccessFailed("Not a directory: " + directory.getAbsolutePath());
        }

        File metaDataDirectory = findMetaDataDirectory(directory);
        if (metaDataDirectory == null) {
            LOGGER.warn("Directory does not seem to hold repository meta data but will be tried regardless: {}", directory.getAbsolutePath());
            metaDataDirectory = directory;
        }

        try {
            return new RepositoryBuilder().setGitDir(metaDataDirectory)
                                          .readEnvironment()
                                          .findGitDir()
                                          .build();
        } catch (IOException ex) {
            throw new RepositoryAccessFailed("No repository found in " + directory, ex);
        }
    }

    private static File findMetaDataDirectory(File directory) {
        if (isMetaDataDirectory(directory)) {
            return directory;
        }

        // only search down by a single level; users are supposed to provide the path to repository root or meta data
        for (File item : listDirectory(directory)) {
            if (!item.isDirectory()) {
                continue;
            }

            if (isMetaDataDirectory(item)) {
                return item;
            }
        }

        return null;
    }

    private static boolean isMetaDataDirectory(File directory) {
        Collection<Predicate<File>> unfulfilledRequirements = new ArrayList<>(META_DATA_DIRECTORY_REQUIREMENTS);

        for (File item : listDirectory(directory)) {
            unfulfilledRequirements.removeIf(requirement -> requirement.test(item));
            if (unfulfilledRequirements.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private static List<File> listDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            throw new IllegalArgumentException("Unable to list directory at " + directory.getAbsolutePath());
        }

        return Arrays.asList(files);
    }

    private static class RepositoryAccessFailed extends RuntimeException {
        private RepositoryAccessFailed(String msg) {
            super(msg);
        }

        private RepositoryAccessFailed(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
