package com.example.magrathea.objectstore.reactive.repository.application;

import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import java.util.List;

public sealed interface CommandResult<T>
    permits CommandResult.Created, CommandResult.Updated, CommandResult.Deleted {

    T aggregate();
    List<ObjectStoreEvent> events();

    record Created<T>(T aggregate, List<ObjectStoreEvent> events, long version)
        implements CommandResult<T> {}

    record Updated<T>(T aggregate, List<ObjectStoreEvent> events, long version)
        implements CommandResult<T> {}

    record Deleted<T>(T aggregate, List<ObjectStoreEvent> events, long version)
        implements CommandResult<T> {}
}
