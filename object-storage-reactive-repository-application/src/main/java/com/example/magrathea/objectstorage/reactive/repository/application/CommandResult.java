package com.example.magrathea.objectstorage.reactive.repository.application;

import com.example.magrathea.objectstorage.domain.event.ObjectStorageEvent;
import java.util.List;

public sealed interface CommandResult<T>
    permits CommandResult.Created, CommandResult.Updated, CommandResult.Deleted {

    T aggregate();
    List<ObjectStorageEvent> events();

    record Created<T>(T aggregate, List<ObjectStorageEvent> events, long version)
        implements CommandResult<T> {}

    record Updated<T>(T aggregate, List<ObjectStorageEvent> events, long version)
        implements CommandResult<T> {}

    record Deleted<T>(T aggregate, List<ObjectStorageEvent> events, long version)
        implements CommandResult<T> {}
}
