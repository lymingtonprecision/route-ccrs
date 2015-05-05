with valid_structures as (
  select distinct
    asr.contract,
    asr.part_no,
    asr.eng_chg_level,
    asr.bom_type_db,
    asr.structure_alternative alternative_no
  from ifsinfo.active_structure_routings asr
  --
  minus
  --
  select distinct
    connect_by_root ps.contract,
    connect_by_root ps.part_no,
    connect_by_root ps.eng_chg_level,
    connect_by_root ps.bom_type_db,
    connect_by_root ps.alternative_no
  from ifsapp.prod_structure ps
  join ifsapp.inventory_part ip
    on ps.contract = ip.contract
    and ps.component_part = ip.part_no
  where ip.type_code_db <> '3'
    and not exists (
      select
        *
      from ifsinfo.active_structure_routings asr
      where ps.contract = asr.contract
        and ps.component_part = asr.part_no
    )
  connect by
    prior ps.contract = ps.contract
    and prior ps.component_part = ps.part_no
),
structure_depths as (
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
from valid_structures vb
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
