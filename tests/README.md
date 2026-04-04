# Zapret2 Tests

## Overview

This directory contains tests for the Zapret2 Magisk module.

## Structure

```
tests/
├── shell/                    # Shell script tests (no device required)
│   └── run-tests.sh          # Main shell test runner
├── integration/              # Integration tests (requires device)
│   └── run-integration-tests.sh
└── README.md                 # This file
```

## Running Tests

### Shell Tests (No Device Required)

```bash
# Run all shell tests
bash tests/shell/run-tests.sh

# Or directly
chmod +x tests/shell/run-tests.sh
./tests/shell/run-tests.sh
```

**Tests included:**
- Module structure validation
- Shell script syntax check
- File permissions verification
- Config file format validation
- VLESS URI parsing
- Network detection logic

### Integration Tests (Requires Device)

```bash
# Connect device via ADB first
adb devices

# Run integration tests
bash tests/integration/run-integration-tests.sh
```

**Tests included:**
- Device connectivity
- Root access verification
- Module installation check
- iptables/NFQUEUE support
- Network status
- VPN binary presence
- Zapret2 start/stop

### Android Unit Tests

```bash
cd android-app
./gradlew test
```

**Tests included:**
- VLESS URI parsing
- Xray JSON generation
- Subscription parsing
- Version comparison

## CI/CD

Tests run automatically on:
- Every push to `main`
- Every pull request
- Before building releases

See `.github/workflows/build.yml` for the CI configuration.

## Adding New Tests

### Shell Tests
Add test functions to `tests/shell/run-tests.sh`:

```bash
test_my_feature() {
    header "My Feature Tests"
    
    # Test logic
    [ condition ] && pass "Test passed" || fail "Test failed"
}
```

### Android Tests
Add test files to `android-app/app/src/test/java/com/zapret2/app/data/`:

```kotlin
@RunWith(AndroidJUnit4::class)
class MyFeatureTest {
    @Test
    fun `test description`() {
        // test code
    }
}
```
