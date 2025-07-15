package com.dahe.v2.modules.miniapp.search.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.miniapp.search.mapper.MiniappSearchTermMapper;
import com.dahe.v2.modules.miniapp.search.model.MiniappSearchTerm;
import com.dahe.v2.modules.miniapp.search.service.MiniappSearchTermService;
import org.springframework.stereotype.Service;

@Service
public class MiniappSearchTermServiceImpl
        extends ServiceImpl<MiniappSearchTermMapper, MiniappSearchTerm>
        implements MiniappSearchTermService {
}
