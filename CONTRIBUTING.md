# Contributing

Contributions are welcome.

## Reporting issues

- Use the bug report template for bugs, the feature request template for ideas.
- Check existing issues first.
- Say which phone and Android version you are on, and which email app you use
  if the problem is about sending.

## Pull requests

1. Fork the repository.
2. Create a feature branch (`git checkout -b feature/my-improvement`).
3. Build with `gradle assembleRelease` (JDK 17, Android SDK 35) or push to your
   fork and let the workflow build it.
4. Install the APK on a real phone and exercise the change.
5. Update `CHANGELOG.md`.
6. Open a pull request.

## Guidelines

- This app is deliberately small. A feature that most people would not use
  belongs in a fork, not here.
- No network code and no new permissions. The app has no internet permission
  on purpose and that is a feature, not an omission.
- Database changes need a Room migration. Never use destructive migration;
  people keep real invoices in this app.
- Keep the Kotlin plain. No DI frameworks, no architecture layers for their own sake.
- No emojis.

## License

By contributing, you agree that your contributions will be licensed under the
MIT License.
