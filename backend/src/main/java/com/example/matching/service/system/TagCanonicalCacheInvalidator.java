package com.example.matching.service.system;

public interface TagCanonicalCacheInvalidator {
    void evictCanonicalCache(Long tagId);
}
