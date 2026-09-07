package com.cherryoj.judgingservice.formal;
final class FormalFailure extends RuntimeException {
    FormalFailure(String code) { super(code); }
}
