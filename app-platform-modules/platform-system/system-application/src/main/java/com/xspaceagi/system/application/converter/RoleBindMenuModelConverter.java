package com.xspaceagi.system.application.converter;

import com.xspaceagi.system.domain.model.MenuNode;
import com.xspaceagi.system.domain.model.RoleBindMenuModel;
import com.xspaceagi.system.application.dto.permission.MenuNodeDto;
import com.xspaceagi.system.application.dto.permission.SysRoleBindMenuDto;
import com.xspaceagi.system.spec.exception.BizException;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色菜单绑定DTO到Model转换器
 */
public class RoleBindMenuModelConverter {

    /**
     * 将Web层DTO转换为Domain层模型
     */
    public static RoleBindMenuModel convertToModel(SysRoleBindMenuDto dto) {
        if (dto == null || dto.getRoleId() == null) {
            throw new BizException("角色ID不能为空");
        }

        RoleBindMenuModel model = new RoleBindMenuModel();
        model.setRoleId(dto.getRoleId());

        List<MenuNodeDto> menuNodes = dto.getFlattenlMenuNodes();
        if (CollectionUtils.isEmpty(menuNodes)) {
            model.setMenuBindResourceList(new ArrayList<>());
            return model;
        }

        List<MenuNode> menuBindInfos = MenuBindModelConverterUtil.convertMenuNodes(menuNodes);
        model.setMenuBindResourceList(menuBindInfos);
        return model;
    }
}
