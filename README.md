# url-shortener
This repo is sample example for url shortening service

## Docker image creation

> mvn clean package

> docker build -t your-dockerhub-username/url-shortener:latest .

> docker push your-dockerhub-username/url-shortener:latest

## Helm Install

> cd helm/url-shortener

> helm install url-shortener . --namespace default --create-namespace \
  -f values-prod.yaml # Use -f for custom values, if any

## Helm Upgrade

> helm upgrade url-shortener . --namespace default \
  -f values-prod.yaml \
  --set image.tag=your-new-image-tag # Override image tag for new deployments

## Deployment using kustomize

base deployment

> kubectl apply -k kustomize/base

deployment on prod
> kubectl apply -k kustomize/overlays/prod



