package com.example.matching.agent.dto.interview;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiTestQuestionSetDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<AiTestQuestionItem> questions;
}
