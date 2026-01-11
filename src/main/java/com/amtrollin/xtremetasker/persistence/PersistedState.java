package com.amtrollin.xtremetasker.persistence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersistedState
{
    private Set<String> completedTaskIds = new HashSet<>();
    private String currentTaskId; // null if none
}
