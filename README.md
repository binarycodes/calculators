# My Application README

- [ ] TODO Replace or update this README with instructions relevant to your application

To start the application in development mode, import it into your IDE and run the `Application` class. 
You can also start the application from the command line by running: 

```bash
./mvnw
```

To build the application in production mode, run:

```bash
./mvnw package
```

To build a Docker image, run:

```bash
docker build -t my-application:latest .
```

If you use commercial components, pass the license key as a build secret:

```bash
docker build --secret id=proKey,src=$HOME/.vaadin/proKey .
```

## Getting Started

The [Quick Start](https://vaadin.com/docs/v25/getting-started/quick-start) tutorial helps you get started with Vaadin in 
around 10 minutes. This tutorial walks you through building a simple application, introducing the core concepts along 
the way.
