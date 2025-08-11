# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

### Testing
```bash
bb test:bb                  # Run all tests
```

### Version Management
```bash
./rel info                  # Show current version and configuration
./rel major                 # Increment major version (X.0.0)
./rel minor                 # Increment minor version (x.X.0)
./rel patch                 # Increment patch version (x.x.X)
./rel changelog             # Display changelog
```

### Build and Development
```bash
./rel build                 # Execute configured build steps
./rel help                  # Display available commands
bb nrepl-server 7888        # Start nREPL server for development
```

## Architecture

This is a Babashka-based release management tool that automates version control and deployment workflows.

### Core Structure
- **Main Script**: `rel` - Babashka executable that implements all commands
- **Configuration**: `releasables.yaml` - Defines build steps, artifacts, and deployment destinations
- **Version File**: `src/rel/version.cljc` - Contains version definition and embedded changelog
- **Build System**: Executes shell commands in a single session, preserving environment variables

### Key Design Patterns

#### Version Management
Versions are stored in source files using regex patterns. The system parses, updates, and maintains semantic versioning through regex replacement in the version file.

#### Changelog Integration
Changelogs are embedded directly in the version file between `<BEGIN CHANGELOG>` and `<END CHANGELOG>` tags, using configurable comment prefixes.

#### Command Dispatch
Commands are dispatched through a table-based system matching command names to functions:
```clojure
(def table
  [{:cmds ["help"] :fn help}
   {:cmds ["major"] :fn major}
   ;; ... other commands
   ])
```

#### Configuration Loading
The tool searches up the directory tree for `releasables.yaml` and loads the first matching releasable configuration.

### Adding New Features

When implementing new commands:
1. Define the function in the `rel` script
2. Add to the dispatch table
3. Update the help text
4. Follow existing error handling patterns with `try/catch` and `System/exit`

When working with shell commands:
- Use `babashka.process/shell` with `:out :string :err :string`
- Execute in a single shell session to preserve environment
- Check exit codes and handle errors appropriately

## Important Implementation Details

- **String Interpolation**: Uses custom `f-string` macro for string formatting
- **YAML Parsing**: Requires special handling of `:key:` format (multiple colons)
- **Shell Commands**: Must be quoted in YAML when containing special characters ($, parentheses)
- **Error Handling**: Fatal errors should call `(System/exit 1)` with clear messages
- **File Updates**: Use slurp/spit pattern with string replacement for file modifications