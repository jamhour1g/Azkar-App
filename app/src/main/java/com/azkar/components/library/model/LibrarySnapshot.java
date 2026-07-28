package com.azkar.components.library.model;

import com.azkar.domain.model.Remembrance;
import java.time.Instant;
import java.util.List;

public record LibrarySnapshot(List<Remembrance> remembrances, List<String> collectionNames, Instant refreshedAt) {}
