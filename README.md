## AWS Secrets Manager Java caching client

[![build](https://github.com/aws/aws-secretsmanager-caching-java/actions/workflows/maven.yml/badge.svg?branch=master&event=push)](https://github.com/aws/aws-secretsmanager-caching-java/actions/workflows/maven.yml)
[![coverage](https://codecov.io/gh/aws/aws-secretsmanager-caching-java/branch/master/graph/badge.svg?token=Kk9RDSuKTE)](https://codecov.io/gh/aws/aws-secretsmanager-caching-java)

The AWS Secrets Manager Java caching client enables in-process caching of secrets for Java applications.

## Getting Started

### Required Prerequisites
To use this client you must have:

* **A Java 8 development environment**

  If you do not have one, go to [Java SE Downloads](https://www.oracle.com/technetwork/java/javase/downloads/index.html) on the Oracle website, then download and install the Java SE Development Kit (JDK). Java 8 or higher is recommended.

An Amazon Web Services (AWS) account to access secrets stored in AWS Secrets Manager and use AWS SDK for Java.

* **To create an AWS account**, go to [Sign In or Create an AWS Account](https://portal.aws.amazon.com/gp/aws/developer/registration/index.html) and then choose **I am a new user.** Follow the instructions to create an AWS account.

* **To create a secret in AWS Secrets Manager**, go to [Creating Secrets](https://docs.aws.amazon.com/secretsmanager/latest/userguide/manage_create-basic-secret.html) and follow the instructions on that page.

* **To download and install the AWS SDK for Java**, go to [Installing the AWS SDK for Java](https://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/java-dg-install-sdk.html) in the AWS SDK for Java documentation and then follow the instructions on that page.

### Download

You can get the latest release from Maven:

```xml
<dependency>
  <groupId>com.amazonaws.secretsmanager</groupId>
  <artifactId>aws-secretsmanager-caching-java</artifactId>
  <version>1.0.2</version>
</dependency>
```

Don't forget to enable the download of snapshot jars from Maven:

```xml
<profiles>
  <profile>
    <id>allow-snapshots</id>
    <activation><activeByDefault>true</activeByDefault></activation>
    <repositories>
      <repository>
        <id>snapshots-repo</id>
        <url>https://aws.oss.sonatype.org/content/repositories/snapshots</url>
        <releases><enabled>false</enabled></releases>
        <snapshots><enabled>true</enabled></snapshots>
      </repository>
    </repositories>
  </profile>
</profiles>
```

### Get Started

The following code sample demonstrates how to get started:

1. Instantiate the caching client.
2. Request secret.

```java
// This example shows how an AWS Lambda function can be written
// to retrieve a cached secret from AWS Secrets Manager caching
// client.
package com.amazonaws.secretsmanager.caching.examples;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import com.amazonaws.secretsmanager.caching.SecretCache;

/**
 * SampleClass.
 */
public class SampleClass implements RequestHandler<String, String> {

    private final SecretCache cache = new SecretCache();

    @Override
    public String handleRequest(String secretId, Context context) {
        final String secret = cache.getSecretString(secretId);
        // Use secret to connect to secured resource.
        return "Success!";
    }
}
```

## License

This library is licensed under the Apache 2.0 License. 
