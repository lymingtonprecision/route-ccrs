select
  psh.contract,
  psh.part_no,
  psh.eng_chg_level,
  psh.bom_type_db bom_type,
  psa.alternative_no,
  --
  (
    select
      count(*)
    from ifsapp.prod_structure psc
    where psh.contract = psc.contract
      and psh.part_no = psc.part_no
      and psh.eng_chg_level = psc.eng_chg_level
      and psh.bom_type_db = psc.bom_type_db
      and psa.alternative_no = psc.alternative_no
  ) component_count,
  --
  ps.component_part,
  decode(
    cp.type_code_db,
    ifsapp.inventory_part_type_api.encode('Manufactured'), 'manufactured',
    'purchased'
  ) component_type,
  decode(
    cp.type_code_db,
    ifsapp.inventory_part_type_api.encode('Manufactured'), 'M',
    null
  ) component_bom_type,
  (
    select
      max(csh.eng_chg_level)
    from ifsapp.prod_structure_head csh
    join ifsapp.prod_struct_alternate csa
      on csh.contract = csa.contract
      and csh.part_no = csa.part_no
      and csh.eng_chg_level = csa.eng_chg_level
      and csh.bom_type_db = csa.bom_type_db
      and csa.objstate = 'Buildable'
    where ps.contract = csh.contract
      and ps.component_part = csh.part_no
      and csh.bom_type_db = 'M'
      and csh.eff_phase_in_date <= trunc(sysdate) + ip.expected_leadtime
      and (
        csh.eff_phase_out_date is null or
        csh.eff_phase_out_date >= trunc(sysdate) + ip.expected_leadtime
      )
  ) component_revision,
  decode(
    cp.type_code_db,
    ifsapp.inventory_part_type_api.encode('Manufactured'), '*',
    null
  ) component_alternate,
  decode(
    cp.type_code_db,
    ifsapp.inventory_part_type_api.encode('Manufactured'),
      mpa.fixed_leadtime_day,
    cp.purch_leadtime
  ) leadtime,
  ifsapp.work_time_calendar_api.get_end_date(
    ifsapp.site_api.get_manuf_calendar_id(cp.contract),
    trunc(sysdate),
    decode(
      cp.type_code_db,
      ifsapp.inventory_part_type_api.encode('Manufactured'),
        mpa.fixed_leadtime_day,
      cp.purch_leadtime
    )
  ) best_end_date
from ifsapp.prod_structure_head psh
join ifsapp.prod_struct_alternate psa
  on psh.contract = psa.contract
  and psh.part_no = psa.part_no
  and psh.eng_chg_level = psa.eng_chg_level
  and psh.bom_type_db = psa.bom_type_db
join ifsapp.prod_structure ps
  on psh.contract = ps.contract
  and psh.part_no = ps.part_no
  and psh.eng_chg_level = ps.eng_chg_level
  and psh.bom_type_db = ps.bom_type_db
  and psa.alternative_no = ps.alternative_no
join ifsapp.inventory_part ip
  on psh.contract = ip.contract
  and psh.part_no = ip.part_no
--
join ifsapp.inventory_part cp
  on ps.contract = cp.contract
  and ps.component_part = cp.part_no
join ifsapp.manuf_part_attribute mpa
  on cp.contract = mpa.contract
  and cp.part_no = mpa.part_no
--
where psh.eff_phase_in_date <= trunc(sysdate) + ip.expected_leadtime
  and (
    psh.eff_phase_out_date is null or
    psh.eff_phase_out_date >= trunc(sysdate) + ip.expected_leadtime
  )
  and psa.objstate = 'Buildable'
  and psh.contract = :contract
  and psh.part_no = :part_no
