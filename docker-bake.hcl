variable "REGISTRY" { default = "docker.io" }
variable "NAMESPACE" { default = "binarycodes" }
variable "APP_NAME" { default = "calculators" }
variable "APP_VERSION" { default = "0.0.0-SNAPSHOT" }

variable "TAG_NAME" { default = "calculators" }
variable "GIT_SHA" { default = "" }

group "default" {
  targets = ["app"]
}

target "app" {
  context    = "."
  dockerfile = "Dockerfile"

  args = {
    APP_NAME    = APP_NAME
    APP_VERSION = APP_VERSION
    GIT_SHA     = GIT_SHA
  }

  secret = ["id=vaadin_license,env=VAADIN_SERVER_LICENSE"]

  tags = [
    "${REGISTRY}/${NAMESPACE}/${TAG_NAME}:${APP_VERSION}",
    "${REGISTRY}/${NAMESPACE}/${TAG_NAME}:latest",
  ]

  platforms = ["linux/amd64", "linux/arm64"]
}