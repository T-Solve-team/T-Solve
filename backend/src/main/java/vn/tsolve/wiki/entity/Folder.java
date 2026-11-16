package vn.tsolve.wiki.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "folder")
public class Folder {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "folder_id")
    private UUID id;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "level", nullable = false)
    private short level;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Folder() {}

    public Folder(long spaceId, UUID parentId, String name, short level, UUID createdBy, Instant now) {
        this.spaceId = spaceId;
        this.parentId = parentId;
        this.name = name;
        this.level = level;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public void edit(String name, UUID parentId, short level, Instant now) {
        this.name = name;
        this.parentId = parentId;
        this.level = level;
        this.updatedAt = now;
    }

    public void changeLevel(short level, Instant now) {
        this.level = level;
        this.updatedAt = now;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public short getLevel() {
        return level;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
