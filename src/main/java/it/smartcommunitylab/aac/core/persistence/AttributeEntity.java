package it.smartcommunitylab.aac.core.persistence;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

@Entity
@Table(name = "attribute_entities", uniqueConstraints = @UniqueConstraint(columnNames = { "attribute_set", "attr_key" }))
public class AttributeEntity {

    @Id
    @GeneratedValue
    private Long id;

    @NotNull
    @Column(name = "attribute_set")
    private String set;

    @NotNull
    @Column(name = "attr_key")
    private String key;

    @NotNull
    @Column(name = "attr_type")
    private String type;

    private String name;
    private String description;

    /**
     * If claim type is multiple
     */
    @Column(name = "is_multiple")
    private boolean multiple;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSet() {
        return set;
    }

    public void setSet(String set) {
        this.set = set;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isMultiple() {
        return multiple;
    }

    public void setMultiple(boolean multiple) {
        this.multiple = multiple;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
