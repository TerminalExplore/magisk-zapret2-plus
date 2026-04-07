# Contributing to Zapret2 Plus

Thank you for your interest in contributing!

## Getting Started

1. Fork the repository
2. Clone your fork
3. Create a feature branch from `dev`

## Branch Model

```
main          ← Production releases (tags here)
└── dev       ← Development integration
    ├── feature/xxx   ← New features
    ├── fix/xxx       ← Bug fixes
    └── docs/xxx      ← Documentation
```

## Development Workflow

1. **Create branch**: `git checkout -b feature/my-feature dev`
2. **Make changes** and test locally
3. **Run tests**:
   ```bash
   # Shell tests
   bash tests/shell/run-tests.sh
   
   # Android unit tests
   cd android-app && ./gradlew test
   ```
4. **Commit**: `git commit -m "feat: add my feature"`
5. **Push**: `git push origin feature/my-feature`
6. **Create Pull Request** to `dev`

## Code Style

### Shell Scripts
- Use [shellcheck](https://www.shellcheck.net/) for linting
- Follow POSIX sh standard
- Use descriptive variable names
- Keep functions small and focused

### Kotlin
- Follow [Kotlin conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use `ktlint` for linting (included in project)
- Prefer immutability
- Use meaningful names

## Commit Messages

Format: `type: short description`

Types:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation
- `chore`: Maintenance (deps, CI, etc.)
- `refactor`: Code refactoring
- `test`: Adding or updating tests

Examples:
```
feat: add server ping functionality
fix: resolve VPN connection timeout
docs: update installation guide
chore: update Xray to v26.3.23
```

## Pull Request Checklist

- [ ] Tests pass locally
- [ ] Code follows style guidelines
- [ ] Commit messages are clear and follow convention
- [ ] Documentation updated (if needed)
- [ ] No console logs or debug code

## Testing

### Shell Tests
```bash
bash tests/shell/run-tests.sh
```

### Android Unit Tests
```bash
cd android-app && ./gradlew test
```

### Lint Checks
```bash
# Shell scripts
shellcheck zapret2/scripts/*.sh

# Kotlin
cd android-app && ./gradlew ktlintCheck
```

## Project Structure

```
magisk-zapret2/
├── android-app/          # Android companion app
│   └── app/src/main/
│       ├── java/         # Kotlin source
│       └── res/          # Resources
├── zapret2/              # Magisk module files
│   ├── scripts/          # Shell scripts
│   ├── bin/              # Binaries (nfqws2, xray)
│   └── lua/              # Lua scripts
├── systemd/              # Linux systemd service
├── tests/                # Test suites
├── docs/                  # Documentation
└── .github/workflows/    # CI/CD
```

## Questions?

- Open an [issue](https://github.com/TerminalExplore/magisk-zapret2-plus/issues) for bugs
- For discussions, use [discussions](https://github.com/TerminalExplore/magisk-zapret2-plus/discussions)
