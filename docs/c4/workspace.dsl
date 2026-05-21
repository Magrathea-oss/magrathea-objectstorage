workspace {
  model {
    user = person "User" "End user interacting via HTTP"
    app = softwareSystem "Magrathea ObjectStorage" "Spring Boot 4 reactive S3-compatible object storage" {
      webapp = container "Web Application" "Spring Boot 4 WebFlux" "S3 API endpoints, business logic, file persistence"
    }
    user -> app "Uses HTTPS — S3-compatible API"
  }

  views {
    systemContext app {
      include user
      include app
      autolayout
    }

    container app {
      include user
      include webapp
      autolayout
    }
  }
}
