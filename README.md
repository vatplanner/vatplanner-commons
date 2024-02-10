# VATPlanner Commons

The artifacts provided through this repository are a collection of various "helper classes" extracted from and in use
throughout several VATPlanner components.

The following artifacts are available, all prefixed `vatplanner-` to avoid naming confusion with another well-known
project:

- `vatplanner-commons-base` is the main module that tries to keep external dependencies as low as reasonably possible.
  It contains various utility classes incl. some basic handling of geo-coordinates as well as interfaces for separation
  from other modules.
- `vatplanner-commons-amqp` contains abstractions to more conveniently work with AMQP using RabbitMQ. It has
  been separated from the base module because it relies on additional dependencies such as `com.rabbitmq:amqp-client`
  that are generally not needed unless you actually want to communicate via AMQP. \
  You may want to combine this module with `vatplanner-commons-crypto` for a really, really basic end-to-end encrypted
  message transport via AMQP (but heed the warnings below).
- `vatplanner-commons-crypto` contains a very basic frontend to employ a *stupidly simplified* level of cryptography
  through PGP via PGPainless and Bouncy Castle. \
  Please note that **security cannot be ensured**. Do not blindly rely on this module unless you know what you are
  doing and have reviewed the code to be free of any concerns for your project. Functionality is specific to the scope
  of the VATPlanner project which means some (if not most) functionality may be insufficient and maybe not even
  cryptographically sane when used in other scopes.
- `vatplanner-commons-fileaccess-jgit` adds a `FileAccessProvider` using Eclipse JGit™ for Git® repository access. \
  Note that this is only a bridge to such repositories using the implementation provided by the upstream project.
  This module shall not be confused with any of the mentioned projects or organizations, see the Acknowledgements
  section below for more information.

Some of these classes may also be useful for projects other than just those associated with VATPlanner. Nevertheless,
this repository should primarily be understood as an essential part of "just" VATPlanner.

At this point no particular guarantees for API stability will be given. Interfaces and availability of classes/methods
may change seemingly "at random" as they are solely aligned with ongoing development of VATPlanner.

## License

This project is released under [MIT license](LICENSE.md).

### Acknowledgements

[Eclipse](https://www.eclipse.org/) and [Eclipse JGit](https://www.eclipse.org/jgit/) are trademarks of
[Eclipse Foundation, Inc.](https://www.eclipse.org/)

[Git](https://git-scm.com/) and the Git logo are either registered trademarks or trademarks of
[Software Freedom Conservancy, Inc.](https://sfconservancy.org/), corporate home of the Git Project, in the
United States and/or other countries.

This project (VATPlanner) has no affiliation with the mentioned trademark holders and shall not be confused with any of
these projects and/or organizations. JGit and Git are only being mentioned to describe this project's dependencies and
to acknowledge the trademarks as requested by the respective parties.

### Additional Disclaimer

Please note that VATPlanner is a **flight simulation** project; check the other repositories and the main website for
more information. You may find several classes, particularly within `vatplanner-commons-base` that *could theoretically*
seem useful for "real-world" applications. However, due to the nature of this project,
**using this project for real-world aviation purposes** (in particular, but not limited to)
**is highly discouraged.**

This project is intended for flight simulation and may deviate from actual real-world standards.
