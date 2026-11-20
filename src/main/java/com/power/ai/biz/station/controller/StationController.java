package com.power.ai.biz.station.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.power.ai.biz.station.entity.PowerStation;
import com.power.ai.biz.station.service.StationService;
import com.power.ai.common.exception.BizException;
import com.power.ai.common.exception.ErrorCode;
import com.power.ai.common.result.Result;
import com.power.ai.common.security.RequireRole;
import com.power.ai.common.security.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/station")
@RequiredArgsConstructor
public class StationController {
    private final StationService stationService;

    @GetMapping("/page")
    public Result<Page<PowerStation>> page(@RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size){
          return Result.ok(stationService.page(new Page<>(current,size)));
    }
    @GetMapping("/{id}")
    public Result<PowerStation> getByID(@PathVariable Long id){
           PowerStation station = stationService.getById(id);
           if(station==null){
               throw new BizException(ErrorCode.NOT_FOUND);
           }
           return Result.ok(station);
    }
    /** 新增电站：仅管理员（电站台账是核心主数据，只有 ADMIN 能维护） */
    @PostMapping
    @RequireRole(UserRole.ADMIN)
    public Result<Void> create(@RequestBody PowerStation station){
        station.setId(null);
        stationService.save(station);
        return Result.ok();
    }

    /** 修改电站：仅管理员 */
    @PutMapping("/{id}")
    @RequireRole(UserRole.ADMIN)
    public Result<Void> update(@PathVariable Long id,@RequestBody PowerStation station){
        station.setId(id);
        if(!stationService.updateById(station)){
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return Result.ok();
    }

    /** 删除电站：仅管理员 */
    @DeleteMapping("/{id}")
    @RequireRole(UserRole.ADMIN)
    public Result<Void> delete(@PathVariable Long id){
        if(!stationService.removeById(id)){
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return Result.ok();

    }
}

