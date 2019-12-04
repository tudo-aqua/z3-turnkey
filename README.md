### The Z3-TurnKey distribution

[The Z3 Theorem Prover](https://github.com/Z3Prover/z3/) is a widely used SMT solver that is written in C and C++. The
authors provide a Java API, however, it is not trivial to set up in a Java project. This project aims to solve this
issue.

#### Why?

The Z3 API is hard-coded to load its libraries from the OS's library directory (e.g., `/usr/lib`, `/usr/local/lib`,
etc. on Linux). These directories should not be writable by normal users. The expected workflow to use Z3 from Java
would therefore require installing a matching version of the Z3 native libraries as an administrator before using the
Java bindings.

Effectively, this makes the creation of Java applications that can be downloaded and run by a user impossible. It would
be preferable to have a Java artifact that
1. ships its own native libraries,
2. can use them without administrative privileges, and
3. can be obtained using [Maven](https://maven.apache.org/).

#### How?

This project consists of two parts:
1. a Java loader, `Z3Loader`, that handles runtime unpacking and linking of the native support libraries, and
2. a build system that create a JAR from the official Z3 distributions that
    1. contains all native support libraries built by the Z3 project,
    2. replaces the hard-coded system library loader with `Z3Loader` by rewriting the Z3 source code, and
    3. bundles all of the required files.
Also, JavaDoc and source JARs are generated for ease of use.

#### License

Z3 is licensed under the [MIT License](https://github.com/Z3Prover/z3/blob/master/LICENSE.txt). The support files in
this project are licensed under the [ISC License](https://opensource.org/licenses/ISC).