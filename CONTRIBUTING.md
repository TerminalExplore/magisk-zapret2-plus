# Contributing to Zapret2 Plus

## Getting Started

1. Fork the repository
2. Clone your fork
3. Create a feature branch from `dev`

## Branch Model

```
main          ← Production releases
└── dev       ← Development integration
    ├── feature/xxx   ← New features
    ├── fix/xxx       ← Bug fixes
    └── docs/xxx      ← Documentation
```

## Development Workflow

1. Create branch: `git checkout -b feature/my-feature dev`
2. Make changes
3. Run tests: `bash tests/shell/run-tests.sh`
4. Commit: `git commit -m "feat: add my feature"`
5. Push: `git push origin feature/my-feature`
6. Create Pull Request to `dev`

## Code Style

### Shell Scripts
- Use `shellcheck` for linting
- Follow POSIX sh standard
- Use descriptive variable names

### Kotlin
- Follow Kotlin conventions
- Use `ktlint` for linting
- Prefer immutability

## Commit Messages

Format: `type: description`

Types:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation
- `chore`: Maintenance
- `refactor`: Code refactoring

## Testing

### Shell Tests
```bash
bash tests/shell/run-tests.sh
```

### Android Unit Tests
```bash
cd android-app && ./gradlew test
```

## Pull Request Checklist

- [ ] Tests pass locally
- [ ] Code follows style guidelines
- [ ] Commit messages are clear
- [ ] Documentation updated (if needed)

## Questions?

Open an issue for discussion.
