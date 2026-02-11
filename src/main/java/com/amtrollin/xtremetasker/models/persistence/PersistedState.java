package com.amtrollin.xtremetasker.models.persistence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersistedState {
    private Set<String> manualCompletedTaskIds = new HashSet<>();
    private Set<String> syncedCompletedTaskIds = new HashSet<>();
    private String currentTaskId;
}
