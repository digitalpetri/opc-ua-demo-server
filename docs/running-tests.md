# Running Tests

This is a single-module Maven project. Unit tests use the `*Test` naming convention and run through
Surefire. Integration tests use the `*IT` naming convention and run through Failsafe during the
`verify` lifecycle.

Before the first Maven or Java command, install the pinned toolchain with `mise install`. If `mise`
reports that the configuration is not trusted, review `.mise.toml`, run
`mise trust .mise.toml`, and retry. Always run Maven and Java through `mise exec --`.

## Run Unit Tests

**Run a specific test class:**

```bash
mise exec -- mvn -q test -Dtest=ClassName
```

**Run a specific test method:**

```bash
mise exec -- mvn -q test -Dtest=ClassName#methodName
```

**Run multiple test classes:**

```bash
mise exec -- mvn -q test -Dtest=ClassOne,ClassTwo
```

**Run tests matching a pattern:**

```bash
mise exec -- mvn -q test -Dtest=*ServiceTest
```

## Run Integration Tests

Run a specific integration-test class with Failsafe's `it.test` selector:

```bash
mise exec -- mvn -q verify -Dit.test=ClassNameIT
```

Run a specific integration-test method:

```bash
mise exec -- mvn -q verify -Dit.test=ClassNameIT#methodName
```

## Run All Tests

Run the full build, including unit tests, integration tests, and the Spotless check:

```bash
mise exec -- mvn -q clean verify
```

If the build fails because Java sources are not formatted, apply the formatter and retry:

```bash
mise exec -- mvn -q spotless:apply
```
