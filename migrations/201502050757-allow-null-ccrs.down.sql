alter table routing_ccr_hist
  modify operation_no not null
  modify work_center_no not null
  modify pre_ccr_buffer not null
  modify post_ccr_buffer not null
;

alter table routing_ccr
  modify operation_no not null
  modify work_center_no not null
  modify pre_ccr_buffer not null
  modify post_ccr_buffer not null
;
