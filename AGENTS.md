# AGENTS.md - Development Guidelines for AI Agents and Human Contributors

## Project Overview

**revision** is a Clojure/Babashka-based release management and version control tool designed to automate software release workflows including version management, changelog maintenance, building, archiving, and deployment.

### Core Purpose
- Automate semantic versioning (major.minor.patch)
- Manage structured changelogs embedded in source files
- Execute configurable build pipelines
- Generate checksums and archives for releases
- Deploy artifacts to multiple destinations

### Technology Stack
- **Language**: Clojure/ClojureScript (`.cljc` files for cross-platform compatibility)
- **Runtime**: Babashka (fast-starting Clojure interpreter)
- **Configuration**: YAML (`releasables.yaml`)
- **Dependencies**:
  - `bling` - Terminal output formatting and colors
  - `clj-yaml` - YAML parsing
  - `babashka.process` - Shell command execution
  - `babashka.cli` - Command-line interface utilities
  - `babashka.fs` - File system operations

## Project Structure

```
revision/
├── rel                      # Main executable script (Babashka shebang)
├── releasables.yaml        # Configuration file defining build/deploy pipeline
├── bb.edn                  # Babashka project configuration
├── src/
│   └── rel/
│       └── version.cljc    # Version definitions and embedded changelog
└── test/
    └── rel_test.cljc       # Test suite
```

## Architecture

### Key Components

1. **Configuration Loading** (`releasables.yaml`)
   - Defines releasable artifacts
   - Specifies version file location and regex pattern
   - Lists build steps (shell commands)
   - Configures deployment destinations

2. **Version Management**
   - Version stored in source file with embedded changelog
   - Regex-based version parsing and updating
   - Semantic versioning rules:
     - `patch`: increments patch (1.2.3 → 1.2.4)
     - `minor`: increments minor, resets patch (1.2.3 → 1.3.0)
     - `major`: increments major, resets minor and patch (1.2.3 → 2.0.0)

3. **Changelog Management**
   - Embedded in source file between `<BEGIN CHANGELOG>` and `<END CHANGELOG>` tags
   - Each entry includes version, date, and bullet-pointed changes
   - Comments use configurable prefix (default: `;;`)

4. **Build System**
   - Executes shell commands sequentially
   - Preserves environment variables across steps (single shell session)
   - Supports command substitution `$(...)` and variables `$VAR`
   - Real-time progress output

## Development Guidelines

### Adding New Commands

1. **Define the function** in `rel` script:
```clojure
(defn new-command [m]
  (let [releasable (load-default (fs/cwd))]
    ;; Implementation here
    ))
```

2. **Add to dispatch table**:
```clojure
(def table
  [{:cmds [] :fn help}
   ;; ... existing commands ...
   {:cmds ["new-command"] :fn new-command}])
```

3. **Update help text** in the `help` function

### Code Conventions

#### Clojure Style
- Use kebab-case for function and variable names
- Prefix private functions with `-`
- Use threading macros (`->`, `->>`) for clarity
- Prefer `let` bindings over nested expressions
- Document functions with docstrings when purpose isn't obvious

#### String Formatting
- Use the custom `f-string` macro for interpolation:
```clojure
(f-string "Version #{major}.#{minor}.#{patch}")
```

#### Error Handling
- Use `try/catch` for external operations (file I/O, shell commands)
- Call `(System/exit 1)` for fatal errors
- Provide clear error messages with context

#### Shell Command Execution
- Always quote commands with special characters in YAML:
```yaml
:build_steps:
  - "echo $USER"                    # Needs quotes
  - "export VAR=$(command)"          # Needs quotes
  - simple-command                  # No quotes needed
```

### Working with YAML Configuration

#### Key Structure
```yaml
:releasables:
  - :id: revision
    :revision:
      :src: src/rel/version.cljc    # Version file path
      :regex: <pattern>              # Version extraction regex
      :comment_prefix: ";;"          # Changelog comment prefix
    :build_steps:
      - <shell command>              # Build commands
    :artefacts:
      - :src: <path>                 # Source file/directory
        :chk: true/false             # Generate checksum?
    :deploy:
      - :dest: <path>                # Deployment destination
```

### Testing

Run tests with:
```bash
bb test:bb
```

Test files should:
- Use `clojure.test` framework
- Follow naming convention `*_test.cljc`
- Include both unit and integration tests
- Test error conditions and edge cases

### Common Patterns

#### Loading Configuration
```clojure
(let [releasable (load-default (fs/cwd))
      {:keys [revision build_steps]} releasable]
  ;; Use configuration
  )
```

#### Executing Shell Commands
```clojure
(let [result (process/shell {:out :string :err :string} "sh" "-c" command)]
  (when (:out result) (print (:out result)))
  (when (not= 0 (:exit result))
    (println "Error!")
    (System/exit 1)))
```

#### Updating Files
```clojure
(let [content (slurp file-path)
      updated (str/replace content pattern replacement)]
  (spit file-path updated))
```

## Command Specifications

### Implemented Commands

| Command | Purpose | Side Effects |
|---------|---------|--------------|
| `help` | Display available commands | None |
| `info` | Show releasable configuration and current version | None |
| `changelog` | Display formatted changelog | None |
| `major` | Increment major version (X.0.0) | Updates version file |
| `minor` | Increment minor version (x.X.0) | Updates version file |
| `patch` | Increment patch version (x.x.X) | Updates version file |
| `build` | Execute build steps | Runs shell commands |

### Planned Commands

| Command | Purpose | Implementation Notes |
|---------|---------|---------------------|
| `tag` | Create git tag for version | Use `process/shell` with git |
| `chk` | Generate SHA512 checksums | Iterate artifacts, compute hashes |
| `md5` | Generate MD5 checksums | Similar to `chk` |
| `archive` | Create release archives | Use tar/zip commands |
| `package` | Build + archive | Compose existing commands |
| `deploy` | Copy artifacts to destinations | File operations or shell commands |

## Contributing Guidelines

### For AI Agents

1. **Understand context**: Read existing code patterns before implementing
2. **Test changes**: Always run `./rel help` to verify syntax
3. **Follow conventions**: Match existing code style and patterns
4. **Update documentation**: Modify help text and this file when adding commands
5. **Preserve functionality**: Don't break existing commands
6. **Use proper escaping**: Quote YAML strings with special characters
7. **Handle errors gracefully**: Add try/catch and meaningful messages

### For Humans

1. **Run tests** before committing: `bb test:bb`
2. **Follow semantic versioning** for version bumps
3. **Write clear commit messages** using conventional commits format
4. **Update changelog** using the tool itself: `./rel patch`
5. **Document new features** in help text and this file
6. **Keep YAML examples** working and instructive

## Debugging Tips

### Common Issues

1. **"nil" in output**: Check for unintended return values from `println`
2. **Environment variables not persisting**: Ensure build steps run in single shell
3. **YAML parsing errors**: Quote strings containing `$`, `()`, or other special chars
4. **Build failures**: Check shell command syntax and permissions
5. **Version regex failures**: Test regex pattern against actual file content

### Debug Techniques

- Add `(println (f-string "Debug: #{variable}"))` for inspection
- Use `(pprint data)` for complex data structures
- Check `(:err result)` from shell commands for error details
- Run commands manually to verify they work: `sh -c "command"`

## Future Enhancements

### High Priority
- [ ] Git integration (`tag` command)
- [ ] Checksum generation (`chk`, `md5` commands)
- [ ] Archive creation (`archive` command)
- [ ] Deployment automation (`deploy` command)

### Medium Priority
- [ ] Multiple releasable support (`--id` flag)
- [ ] Dry-run mode (`--dryrun` flag)
- [ ] Version validation and rollback
- [ ] Pre/post hooks for commands

### Low Priority
- [ ] Plugin system for custom commands
- [ ] Web UI for release management
- [ ] Integration with CI/CD systems
- [ ] Automated release notes generation

## Resources

- [Babashka Documentation](https://book.babashka.org/)
- [Clojure Style Guide](https://guide.clojure.style/)
- [Semantic Versioning](https://semver.org/)
- [Conventional Commits](https://www.conventionalcommits.org/)

---

*This document serves as a comprehensive guide for both AI agents and human developers working on the revision project. Keep it updated as the project evolves.*
