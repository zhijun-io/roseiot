package io.roseiot.shared.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.Clock;
import java.time.Instant;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/** Fills mutable-row audit timestamps for MyBatis-Plus insert and entity-update operations. */
@Component
final class AuditMetaObjectHandler implements MetaObjectHandler {

    private final Clock clock;

    AuditMetaObjectHandler(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        Instant now = Instant.now(clock);
        strictInsertFill(metaObject, "createdAt", Instant.class, now);
        strictInsertFill(metaObject, "updatedAt", Instant.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        setFieldValByName("updatedAt", Instant.now(clock), metaObject);
    }
}
