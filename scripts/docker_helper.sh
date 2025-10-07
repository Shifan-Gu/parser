#!/bin/bash

# Docker helper script for Dota 2 Parser
# This script provides convenient commands for Docker operations

set -e

show_usage() {
    echo "Usage: $0 [COMMAND]"
    echo ""
    echo "Commands:"
    echo "  build           Build the Docker image"
    echo "  start           Start services (production)"
    echo "  start-dev       Start services (development mode)"
    echo "  stop            Stop all services"
    echo "  restart         Restart all services"
    echo "  logs            Show logs from parser service"
    echo "  logs-db         Show logs from database service"
    echo "  shell           Open shell in parser container"
    echo "  db-shell        Open PostgreSQL shell"
    echo "  clean           Remove containers and volumes"
    echo "  rebuild         Clean and rebuild everything"
    echo ""
    echo "Examples:"
    echo "  $0 start        # Start production setup"
    echo "  $0 start-dev    # Start development setup with live reloading"
    echo "  $0 logs         # View parser logs"
    echo "  $0 db-shell     # Connect to PostgreSQL"
}

case "${1:-}" in
    build)
        echo "Building Docker image..."
        docker-compose build
        ;;
    start)
        echo "Starting production services..."
        docker-compose up -d
        echo "Services started. Parser available at http://localhost:5600"
        ;;
    start-dev)
        echo "Starting development services..."
        docker-compose -f docker-compose.dev.yml up -d
        echo "Development services started. Parser available at http://localhost:5600"
        ;;
    stop)
        echo "Stopping services..."
        docker-compose down
        docker-compose -f docker-compose.dev.yml down
        ;;
    restart)
        echo "Restarting services..."
        $0 stop
        $0 start
        ;;
    logs)
        docker-compose logs -f parser
        ;;
    logs-dev)
        docker-compose -f docker-compose.dev.yml logs -f parser
        ;;
    logs-db)
        docker-compose logs -f postgres
        ;;
    shell)
        docker-compose exec parser bash
        ;;
    shell-dev)
        docker-compose -f docker-compose.dev.yml exec parser bash
        ;;
    db-shell)
        echo "Connecting to PostgreSQL..."
        docker-compose exec postgres psql -U postgres -d dota_parser
        ;;
    clean)
        echo "Cleaning up containers and volumes..."
        docker-compose down -v
        docker-compose -f docker-compose.dev.yml down -v
        docker system prune -f
        ;;
    rebuild)
        echo "Rebuilding everything..."
        $0 clean
        $0 build
        $0 start
        ;;
    --help|-h)
        show_usage
        ;;
    *)
        echo "Error: Unknown command '$1'"
        show_usage
        exit 1
        ;;
esac
