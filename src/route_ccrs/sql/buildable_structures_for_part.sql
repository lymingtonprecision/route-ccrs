with buildable_structures as (
  select distinct
    asr.contract,
    asr.part_no,
    asr.bom_type_db,
    asr.eng_chg_level,
    asr.structure_alternative alternative_no
  from ifsinfo.active_structure_routings asr
  where asr.contract = :contract
    and asr.part_no = :part_no
),
active_components as (
  select
    ps.contract,
    ps.part_no,
    ps.bom_type_db,
    ps.eng_chg_level,
    ps.alternative_no,
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
    decode(
      cp.type_code_db,
      ifsapp.inventory_part_type_api.encode('Manufactured'), (
        select
          max(asr.eng_chg_level)
        from ifsinfo.active_structure_routings asr
        where ps.contract = asr.contract
          and ps.component_part = asr.part_no
          and asr.bom_type_db = 'M'
          and asr.structure_alternative = '*'
          and trunc(sysdate) + ip.expected_leadtime between
            asr.phase_in_date and
            asr.phase_out_date
       ),
       null
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
  from ifsapp.prod_structure ps
  join ifsapp.inventory_part ip
    on ps.contract = ip.contract
    and ps.part_no = ip.part_no
  join ifsapp.inventory_part cp
    on ps.contract = cp.contract
    and ps.component_part = cp.part_no
  join ifsapp.manuf_part_attribute mpa
    on cp.contract = mpa.contract
    and cp.part_no = mpa.part_no
  where cp.type_code_db <> ifsapp.inventory_part_type_api.encode('Manufactured')
     or exists (
      select
        asr.eng_chg_level
      from ifsinfo.active_structure_routings asr
      where ps.contract = asr.contract
        and ps.component_part = asr.part_no
        and asr.bom_type_db = 'M'
        and asr.structure_alternative = '*'
        and trunc(sysdate) + ip.expected_leadtime between
          asr.phase_in_date and
          asr.phase_out_date
     )
)
select
  psh.contract,
  psh.part_no,
  psh.eng_chg_level,
  psh.bom_type_db bom_type,
  psh.alternative_no,
  --
  count(ac.component_part) over (
    partition by
      psh.contract,
      psh.part_no,
      psh.eng_chg_level,
      psh.bom_type_db,
      psh.alternative_no
  ) component_count,
  --
  ac.component_part,
  ac.component_type,
  ac.component_bom_type,
  ac.component_revision,
  --
  ac.component_alternate,
  ac.leadtime,
  ac.best_end_date
from buildable_structures psh
join ifsapp.prod_struct_alternate psa
  on psh.contract = psa.contract
  and psh.part_no = psa.part_no
  and psh.eng_chg_level = psa.eng_chg_level
  and psh.bom_type_db = psa.bom_type_db
  and psh.alternative_no = psa.alternative_no
join active_components ac
  on psh.contract = ac.contract
  and psh.part_no = ac.part_no
  and psh.eng_chg_level = ac.eng_chg_level
  and psh.bom_type_db = ac.bom_type_db
  and psh.alternative_no = ac.alternative_no
--
order by
  psh.part_no,
  psh.bom_type_db,
  psh.eng_chg_level,
  psh.alternative_no
