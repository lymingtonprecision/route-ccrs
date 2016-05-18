with structure_depths as (
  select
    contract,
    part_no,
    eng_chg_level,
    bom_type_db,
    alternative_no,
    max(lvl) depth
  from (
    select
      connect_by_root ps.contract contract,
      connect_by_root ps.part_no part_no,
      connect_by_root ps.eng_chg_level eng_chg_level,
      connect_by_root ps.bom_type_db bom_type_db,
      connect_by_root ps.alternative_no alternative_no,
      level - 1 lvl
    from ifsapp.prod_structure ps
    connect by
      prior ps.contract = ps.contract
      and prior ps.component_part = ps.part_no
  )
  group by
    contract,
    part_no,
    eng_chg_level,
    bom_type_db,
    alternative_no
)
select
  vb.part_no id,
  min(sd.depth) min_depth
from ifsinfo.valid_product_structures vb
join structure_depths sd
  on vb.contract = sd.contract
  and vb.part_no = sd.part_no
  and vb.eng_chg_level = sd.eng_chg_level
  and vb.bom_type_db = sd.bom_type_db
  and vb.alternative_no = sd.alternative_no
group by
  vb.part_no
having
  min(sd.depth) >= nvl(:min_depth, 0)
order by
  dbms_random.value
