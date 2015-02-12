create table assembly_ccr_hist (
  contract varchar2(20) not null,
  part_no varchar2(25) not null,
  eng_chg_level varchar2(3) not null,
  structure_alternative_no varchar2(20) not null,
  bom_type_db varchar2(3) not null,
  routing_revision_no varchar2(4) not null,
  routing_alternative_no varchar2(20) not null,
  --
  operation_no number,
  work_center_no varchar2(5),
  total_touch_time number not null,
  pre_ccr_buffer number,
  post_ccr_buffer number,
  best_end_date date not null,
  --
  ccc_part_no varchar2(25),
  ccc_bom_type_db varchar2(3),
  ccc_eng_chg_level varchar2(3),
  ccc_structure_alternative varchar2(20),
  ccc_routing_revision_no varchar2(4),
  ccc_routing_alternative_no varchar2(20),
  ccc_best_end_date date,
  --
  calculated_at date default sysdate not null,
  rowversion date default sysdate not null,
  --
  constraint assembly_ccr_hist_pk primary key (
    contract,
    part_no,
    eng_chg_level,
    structure_alternative_no,
    bom_type_db,
    routing_revision_no,
    routing_alternative_no,
    calculated_at
  )
);

create table assembly_ccr (
  contract varchar2(20) not null,
  part_no varchar2(25) not null,
  eng_chg_level varchar2(3) not null,
  structure_alternative_no varchar2(20) not null,
  bom_type_db varchar2(3) not null,
  routing_revision_no varchar2(4) not null,
  routing_alternative_no varchar2(20) not null,
  --
  operation_no number,
  work_center_no varchar2(5),
  total_touch_time number not null,
  pre_ccr_buffer number,
  post_ccr_buffer number,
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
  ccc_part_no varchar2(25),
  ccc_bom_type_db varchar2(3),
  ccc_eng_chg_level varchar2(3),
  ccc_structure_alternative varchar2(20),
  ccc_routing_revision_no varchar2(4),
  ccc_routing_alt_no varchar2(20),
  ccc_best_end_date date,
  --
  last_ccc_part_no varchar2(25),
  last_ccc_bom_type_db varchar2(3),
  last_ccc_eng_chg_level varchar2(3),
  last_ccc_structure_alternative varchar2(20),
  last_ccc_routing_revision_no varchar2(4),
  last_ccc_routing_alt_no varchar2(20),
  last_ccc_best_end_date date,
  --
  rowversion date default sysdate not null,
  --
  constraint assembly_ccr_pk primary key (
    contract,
    part_no,
    eng_chg_level,
    structure_alternative_no,
    bom_type_db,
    routing_revision_no,
    routing_alternative_no
  )
);

create index assembly_ccr_ix1 on assembly_ccr (
  work_center_no,
  last_work_center_no,
  ccr_as_of
);

create index assembly_ccr_ix2 on assembly_ccr (
  ccr_as_of
);
