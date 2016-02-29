# cedar-terminology-server

A wrapper for the BioPortal API that simplifies the access to BioPortal ontologies and value sets from CEDAR tools.

This project is implemented in Java using [Play Framework](http://www.playframework.com/).

The multimodule structure of this project is based on the template [play-cedar-service](https://github.com/metadatacenter/play-cedar-service),
which allows the use Maven with the Play Framework.

The project contains two subdirectories:

- cedar-terminology-server-core: Core server functionality
- cedar-terminology-server-play: Play-based interface to server

## Versions

* Java: 1.8
* Play Framework: 2.3.8

## Getting started

Clone the project:

    git clone https://github.com/metadatacenter/cedar-terminology-server.git

## Running the tests

Go to the project root folder and execute the Maven "test" goal:

    mvn test

## Starting the services

At the project root folder:

    mvn install
    cd cedar-terminology-server-play
    mvn play2:run

By default, the services will be running at http://localhost:9000.

## Documentation

This project uses the Swagger Framework (http://swagger.io/), which provides interactive documentation for the terminology server. The documentation is shown when opening the default page (http://localhost:9000).

Note: The 'dist' folder from the swagger-ui project has been copied to the 'public/swagger-ui' folder and a light customization was done using the instructions provided at [https://github.com/swagger-api/swagger-ui](https://github.com/swagger-api/swagger-ui)

## IntelliJ IDEA 14 configuration

Instructions on how to configure IntelliJ 14 to build and run this project are available [here] (https://github.com/metadatacenter/cedar-docs/wiki/Maven-Play-project-configuration-in-IntelliJ-IDEA-14).