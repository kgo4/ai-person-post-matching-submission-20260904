package com.example.matching.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.example.matching.port.kg.GraphQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GraphQueryPortAdapter implements GraphQueryPort {

    private final KgGraphNodeMapper kgGraphNodeMapper;

    @Override
    public Map<String, Long> countNodesByType() {
        return kgGraphNodeMapper.selectList(Wrappers.<KgGraphNode>lambdaQuery()).stream()
                .collect(Collectors.groupingBy(KgGraphNode::getNodeType, Collectors.counting()));
    }
}
