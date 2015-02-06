create table routing_ccr_hist (
  contract varchar2(20) not null,
  part_no varchar2(25) not null,
  bom_type_db varchar2(3) not null,
  routing_revision_no varchar2(4) not null,
  routing_alternative_no varchar2(20) not null,
  --
  operation_no number not null,
  work_center_no varchar2(5) not null,
  total_touch_time number not null,
  pre_ccr_buffer number not null,
  post_ccr_buffer number not null,
  best_end_date date not null,
  --
  calculated_at date default sysdate not null,
  rowversion date default sysdate not null,
  --
  constraint routing_ccr_hist_pk primary key (
    contract,
    part_no,
    bom_type_db,
    routing_revision_no,
    routing_alternative_no,
    calculated_at
  )
);

create table routing_ccr (
  contract varchar2(20) not null,
  part_no varchar2(25) not null,
  bom_type_db varchar2(3) not null,
  routing_revision_no varchar2(4) not null,
  routing_alternative_no varchar2(20) not null,
  --
  operation_no number not null,
  work_center_no varchar2(5) not null,
  total_touch_time number not null,
  pre_ccr_buffer number not null,
  post_ccr_buffer number not null,
  best_end_date date not null,
  ccr_as_of date default sysdate not null,
  --
  last_operation_no number,
  last_work_center_no varchar2(5),
  last_total_touch_time number,
  last_pre_ccr_buffer number,
  last_post_ccr_buffer number,
  last_best_end_date date,
  last_ccr_as_of date,
  --
  rowversion date default sysdate not null,
  --
  constraint routing_ccr_pk primary key (
    contract,
    part_no,
    bom_type_db,
    routing_revision_no,
    routing_alternative_no
  )
);

create index routing_ccr_ix1 on routing_ccr (
  work_center_no,
  last_work_center_no,
  ccr_as_of
);

create index routing_ccr_ix2 on routing_ccr (
  ccr_as_of
);
