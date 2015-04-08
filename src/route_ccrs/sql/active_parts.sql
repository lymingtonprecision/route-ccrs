select
  ip.part_no,
  mpa.low_level low_level_code
from ifsapp.inventory_part ip
join ifsapp.manuf_part_attribute mpa
  on ip.contract = mpa.contract
  and ip.part_no = mpa.part_no
where ip.part_status in (
    select
      ps.part_status
    from ifsapp.inventory_part_status_par ps
    where ps.demand_flag_db = 'Y'
       or ps.onhand_flag_db = 'Y'
       or ps.supply_flag_db = 'Y'
  )
  and exists (
    select
      *
    from ifsinfo.active_structure_routings asr
    where ip.contract = asr.contract
      and ip.part_no = asr.part_no
  )
