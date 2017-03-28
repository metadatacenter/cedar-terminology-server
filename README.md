# cedar-terminology-server

[![Build Status](https://travis-ci.org/metadatacenter/cedar-terminology-server.svg?branch=master)](https://travis-ci.org/metadatacenter/cedar-terminology-server)

A wrapper for the BioPortal API that simplifies the access to BioPortal ontologies and value sets from CEDAR tools.

This project is implemented in Java using Dropwizard.

The project contains two subdirectories:

- cedar-terminology-server-core: Core server functionality
- cedar-terminology-server-application: Dropwizard-based interface to server

## Versions

* Java: 1.8

## Getting started

Clone the project:

    git clone https://github.com/metadatacenter/cedar-terminology-server.git

## Running the tests

Go to the project root folder and execute the Maven "test" goal:

    mvn test

## Starting the services

At the project root folder:

    mvn install
    cd cedar-terminology-server-application
    java \
          -jar $CEDAR_HOME/cedar-terminology-server/cedar-terminology-server-application/target/cedar-terminology-server-application-*.jar \
          server \
          "$CEDAR_HOME/cedar-terminology-server/cedar-terminology-server-application/config.yml"

By default, the services will be running at http://localhost:9004.

## Documentation

This project uses the Swagger Framework (http://swagger.io/), which provides interactive documentation for the terminology server. The documentation is shown when opening the default page (http://localhost:9004).

Note: The 'dist' folder from the swagger-ui project has been copied to the 'public/swagger-ui' folder and a light customization was done using the instructions provided at [https://github.com/swagger-api/swagger-ui](https://github.com/swagger-api/swagger-ui)

## Questions

If you have questions about this repository, please subscribe to the [CEDAR Developer Support
mailing list](https://mailman.stanford.edu/mailman/listinfo/cedar-developers).
After subscribing, send messages to cedar-developers at lists.stanford.edu.


