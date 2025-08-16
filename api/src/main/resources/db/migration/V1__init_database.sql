-- V1__init_database.sql
create schema if not exists booking;

create extension if not exists "uuid-ossp";
create extension if not exists btree_gist;
