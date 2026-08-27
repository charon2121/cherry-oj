package com.cherryoj.userservice.application;

import java.util.List;

public record UserPage(List<UserView> items, int page, int size, long totalElements, int totalPages) {
}
