package com.azkar.data.repo;

import java.util.List;

public interface FtsSearch {
    /** returns remembrance IDs ordered by SQLite FTS rank */
    List<Long> search(String expression);
}
