variable "REGISTRY" { default = "docker.io" }
variable "NAMESPACE" { default = "binarycodes" }
variable "APP_NAME" { default = "calculators" }
variable "APP_VERSION" { default = "0.0.0-SNAPSHOT" }

variable "TAG_NAME" { default = "calculators" }
variable "VAADIN_SERVER_LICENSE" { default = "" }
variable "GIT_SHA" { default = "" }

group "default" {
  targets = ["app"]
}

target "app" {
  context    = "."
  dockerfile = "Dockerfile"

  args = {
    APP_NAME              = APP_NAME
    APP_VERSION           = APP_VERSION
    VAADIN_SERVER_LICENSE = VAADIN_SERVER_LICENSE
    GIT_SHA               = GIT_SHA
  }

  tags = [
    "${REGISTRY}/${NAMESPACE}/${TAG_NAME}:${APP_VERSION}",
    "${REGISTRY}/${NAMESPACE}/${TAG_NAME}:latest",
  ]

  platforms = ["linux/amd64", "linux/arm64"]
}