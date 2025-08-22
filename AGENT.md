# Agent Development Guide

## Build/Test Commands
- Run all tests: `bb test:bb`
- Run specific namespace tests: `bb test:bb --nses rel.core-test`
- Run specific test: `bb test:bb --vars rel.core-test/parse-revision-test`
- Start REPL for development: `bb nrepl:mcp` (port 7888)
- Execute main script: `./rel <command>`

## Code Style Guidelines
- **Imports**: Use standard aliases (`[clojure.string :as str]`, `[clojure.test :refer [deftest is testing]]`)
- **Formatting**: Follow standard Clojure indentation (2 spaces)
- **Functions**: Use descriptive kebab-case names (`parse-revision`, `increment-patch-version`)
- **Destructuring**: Prefer `{:keys [...]}` for map destructuring
- **Testing**: Group related assertions with `testing` blocks and descriptive strings
- **Error handling**: Use structured maps for return values with consistent keys (`:major`, `:minor`, `:patch`)
- **Regex**: Use named groups in regex patterns for clarity (`(?<major>\\d+)`)
- **Documentation**: Functions should have clear single responsibilities
- **Namespaces**: Use qualified aliases (`core/parse-revision`)

## Project Structure
This is a Babashka CLI tool for version management with `.cljc` files for cross-platform compatibility.