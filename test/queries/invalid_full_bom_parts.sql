with invalid_structures as (
  select distinct
    asr.contract,
    asr.part_no,
    asr.eng_chg_level,
    asr.bom_type_db,
    asr.structure_alternative alternative_no
  from ifsinfo.active_structure_routings asr
  --
  intersect
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
)
select
  part_no id
from invalid_structures
group by
  part_no
order by
  dbms_random.value
