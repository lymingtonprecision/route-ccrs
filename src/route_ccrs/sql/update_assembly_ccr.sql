update assembly_ccr
set
  operation_no = :operation_no,
  total_touch_time = :total_touch_time,
  pre_ccr_buffer = :pre_ccr_buffer,
  post_ccr_buffer = :post_ccr_buffer,
  best_end_date = :best_end_date,
  ccc_part_no = :ccc_part_no,
  ccc_bom_type_db = :ccc_bom_type,
  ccc_eng_chg_level = :ccc_eng_chg_level,
  ccc_structure_alternative = :ccc_structure_alternative,
  ccc_routing_revision_no = :ccc_routing_revision,
  ccc_routing_alt_no = :ccc_routing_alternative,
  ccc_best_end_date = :ccc_best_end_date,
  rowversion = sysdate
where contract = :contract
  and part_no = :part_no
  and eng_chg_level = :eng_chg_level
  and structure_alternative_no = :alternative_no
  and bom_type_db = :bom_type_db
  and routing_revision_no = :routing_revision_no
  and routing_alternative_no = :routing_alternative_no
