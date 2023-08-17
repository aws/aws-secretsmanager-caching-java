package com.amazonaws.secretsmanager.caching.internal;

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
}
