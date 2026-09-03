package com.example.matching.service.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.dto.system.ExtendFieldConfigDTO;
import com.example.matching.entity.system.SysExtendField;
import com.example.matching.vo.system.ExtendFieldVO;

import java.util.List;

/**
 * 扩展字段元数据 服务接口
 */
public interface SysExtendFieldService extends IService<SysExtendField> {

    /** 保存扩展字段 */
    void saveField(ExtendFieldConfigDTO dto);

    /** 按业务模块查询 */
    List<ExtendFieldVO> listByModule(String businessModule);

    /** 分页查询 */
    IPage<ExtendFieldVO> pageFields(IPage<SysExtendField> page, String businessModule);
}
