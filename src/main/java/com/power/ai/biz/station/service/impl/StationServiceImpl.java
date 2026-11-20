package com.power.ai.biz.station.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.power.ai.biz.station.entity.PowerStation;
import com.power.ai.biz.station.mapper.PowerStationMapper;
import com.power.ai.biz.station.service.StationService;
import org.springframework.stereotype.Service;

@Service
public class StationServiceImpl extends ServiceImpl<PowerStationMapper,PowerStation> implements StationService{
}
