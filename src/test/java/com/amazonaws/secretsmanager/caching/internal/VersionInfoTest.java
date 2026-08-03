package com.amazonaws.secretsmanager.caching.internal;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.amazonaws.secretsmanager.caching.cache.internal.VersionInfo;

public class VersionInfoTest {

    @Test
    public void versionInfoIsValid() {
        String ua = VersionInfo.USER_AGENT;
        Pattern p = Pattern.compile("AwsSecretCache/\\d+.\\d+.\\d+");

        Assert.assertTrue(p.matcher(ua).matches(), "User agent " + ua + " is not valid");
    }

    @Test
    public void releaseVersionResolvesToSemver() {
        Pattern p = Pattern.compile("\\d+\\.\\d+\\.\\d+");

        Assert.assertTrue(p.matcher(VersionInfo.RELEASE_VERSION).matches(),
                "RELEASE_VERSION should resolve to a semver but was " + VersionInfo.RELEASE_VERSION);
    }

    @Test
    public void readVersionParsesValidStream() {
        InputStream in = new ByteArrayInputStream("version=1.2.3".getBytes(StandardCharsets.UTF_8));

        Assert.assertEquals(VersionInfo.readVersion(in), "1.2.3");
    }

    @Test
    public void readVersionFallsBackOnNullStream() {
        Assert.assertEquals(VersionInfo.readVersion(null), VersionInfo.UNKNOWN_VERSION);
    }

    @Test
    public void readVersionFallsBackWhenKeyMissing() {
        InputStream in = new ByteArrayInputStream("foo=bar".getBytes(StandardCharsets.UTF_8));

        Assert.assertEquals(VersionInfo.readVersion(in), VersionInfo.UNKNOWN_VERSION);
    }

    @Test
    public void readVersionFallsBackOnUnfilteredPlaceholder() {
        InputStream in = new ByteArrayInputStream("version=${project.version}".getBytes(StandardCharsets.UTF_8));

        Assert.assertEquals(VersionInfo.readVersion(in), VersionInfo.UNKNOWN_VERSION);
    }

    @Test
    public void userAgentSuffixAppendsCallerSuffix() {
        Assert.assertEquals(VersionInfo.userAgentSuffix("MyApp/9.9"), "MyApp/9.9 " + VersionInfo.USER_AGENT);
    }

    @Test
    public void userAgentSuffixWithNullCaller() {
        Assert.assertEquals(VersionInfo.userAgentSuffix(null), VersionInfo.USER_AGENT);
    }

    @Test
    public void userAgentSuffixWithBlankCaller() {
        Assert.assertEquals(VersionInfo.userAgentSuffix("   "), VersionInfo.USER_AGENT);
    }
}
