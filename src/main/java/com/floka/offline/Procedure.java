/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.floka.offline;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.object.StoredProcedure;
import org.springframework.stereotype.Component;

/**
 *
 * @author floka-ab
 */
@Component("procedure")
public class Procedure {

    private final Log log = LogFactory.getLog(getClass());
    private List<Map<String, Object>> cl;
    @Autowired
    private DataSource ds;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private @Value("${ug.gragPath}") String gragPath;

    public String getOsk(String fio, String im, String ot, String dd, String mm, String yyyy) {
        String sql = "select FAMILY, NAME, LNAME, BIRTHD, BIRTHM, BIRTHY, INTID from osk_person_name where FAMILY like ? and NAME like ? and LNAME like ? and BIRTHD like ? and BIRTHM like ? and BIRTHY like ?  and rownum < 10";
        String ret = "";
        cl = jdbcTemplate.queryForList(sql,
                new Object[]{fio, im, ot, dd, mm, yyyy});
        //log.info(cl);
        OskSpr oskSpr = new OskSpr(ds);
        for (Map<String, Object> c : cl) {
            ret += oskSpr.execute(Long.parseLong(c.get("INTID").toString()), 1L);
            ret += oskSpr.execute(Long.parseLong(c.get("INTID").toString()), 2L);
        }
        if (ret.isEmpty()) {
            return "<br>Нет данных о судимости.\n";
        }
        return "<br><b>Судимости:</b><br>" + ret;
    }

    public class OskSpr extends StoredProcedure {

        private static final String sql = "osk_spr";

        public OskSpr(DataSource ds) {
            super(ds, sql);
            declareParameter(new SqlOutParameter("param_out", Types.VARCHAR));
            declareParameter(new SqlParameter("param_in", Types.NUMERIC));
            declareParameter(new SqlParameter("param_in1", Types.NUMERIC));
            setFunction(true);//you must set this as it distinguishes it from a sproc
            compile();
        }

        public String execute(Long rdsId, Long p) {
            String ret = "";
            Map in = new HashMap();
            in.put("param_in", rdsId);
            in.put("param_in1", p);
            Map out = execute(in);
            //log.info(out);
            Object oo = out.get("param_out");
            if (!out.isEmpty() && out != null && oo != null) {
                ret = "\n<pre>" + out.get("param_out").toString() + "<pre>\n";
            }
            return ret;
        }
    }

    public String getAdm(String fio, String im, String ot, String dd, String mm, String yyyy) {
        String sql = "select t.fam, t.imj, t.otch , t.d_rojd||'.'||t.m_rojd||'.'||t.y_rojd dr,\n"
                + " t.d_sover||'.'||t.m_sover||'.'||t.y_sover ds,\n"
                + " (select text from s2i_klass s2i where s2i.nomer = '239' and s2i.kod_gr = t.pr_vid1 and s2i.kod = t.pr_vid2)\n"
                + " ||DECODE(t.fabula,NULL,' ',' ('||t.fabula||') ') statya,\n"
                + " (select text from s2i_klass s2i where s2i.nomer = '243' and s2i.kod = t.MERA_NAK)\n"
                + " ||DECODE(t.raz_nak,NULL,' ',' '||t.raz_nak||' руб.') MERA_NAK_s,\n"
                + " DECODE(t.doljn,NULL,' ',t.doljn) doljn,\n"
                + " DECODE(t.kraj,NULL,' ',\n"
                + " t.kraj||','||t.rajon||','||t.kw_punkt||', ул. '||t.ulica||', дом '||t.n_dom||\n"
                + " DECODE(t.korpus,NULL,' ',', кор. '||t.korpus)||\n"
                + " DECODE(t.kw,NULL,' ',', кв. '||t.kw)) adr_p,\n"
                + " DECODE(t.kraj_sover,NULL,' ',\n"
                + " t.kraj_sover||','||t.rajon_sover||','||t.kw_punkt_sover||', ул. '||t.ulica_sover||', дом '||t.n_dom_sover||\n"
                + " DECODE(t.korpus_sover,NULL,' ',', кор. '||t.korpus_sover)||\n"
                + " DECODE(t.kw_sover,NULL,' ',', кв. '||t.kw_sover)) adr_s\n"
                + " from t031 t where\n"
                + " t.fam like ? and t.imj like ? and t.otch like ?\n"
                + " and rownum < 20";
        String ret = "";
        cl = jdbcTemplate.queryForList(sql,
                new Object[]{fio, im, ot});
        //log.info(cl);
        for (Map<String, Object> c : cl) {
            ret += "<tr><td>" + c.get("fam")
                    + "</td><td>" + c.get("imj")
                    + "</td><td>" + c.get("otch")
                    + "</td><td>" + c.get("dr")
                    + "</td><td>" + c.get("ds")
                    + "</td><td>" + c.get("statya")
                    + "</td><td>" + c.get("mera_nak_s")
                    + "</td><td>" + c.get("doljn")
                    + "</td><td>" + c.get("adr_p")
                    + "</td><td>" + c.get("adr_s") + "</td></tr>";
        }
        if (ret.isEmpty()) {
            return "<br>Нет данных об административных правонарушениях\n";
        }
        return "<br><b>Административные правонарушения</b><br><table border=1><tr>\n"
                + " <th>Фамилия</th>\n"
                + " <th>Имя</th>\n"
                + " <th>Отчество</th>\n"
                + " <th>Дата рожд.</th>\n"
                + " <th>Дата<br>совершения</th>\n"
                + " <th>Адм. статья</th>\n"
                + " <th>Решение</th>\n"
                + " <th>Место работы</th>\n"
                + " <th>Адрес прописки</th>\n"
                + " <th>Адрес совершения</th>\n"
                + " </tr>" + ret + "</table><br>";
    }

    public String getChs(String fio, String im, String ot, String dd, String mm, String yyyy) {
        String sql = "select PRED, OGRN, INN, FAM, NAM, FAT, DR, ADR, PAS, KOD, PRIM, RAB, trim(IN_DATE) IN_DATE from SPISOK where"
                + " fam like ? and nam like ? and fat like ?"
                + " and rownum < 20";
        String ret = "";
        cl = jdbcTemplate.queryForList(sql,
                new Object[]{fio, im, ot});
        //log.info(cl);
        for (Map<String, Object> c : cl) {
            ret += "<tr><td>" + c.get("pred") + "," + c.get("ogrn") + "," + c.get("inn")
                    + "</td><td>" + c.get("fam") + ","
                    + c.get("nam") + ","
                    + c.get("fat") + ","
                    + c.get("dr") + ","
                    + c.get("adr") + ","
                    + c.get("pas") + ","
                    + c.get("prim") + ","
                    + c.get("rab") + ","
                    + "</td><td>" + c.get("kod") + "," + c.get("in_date")
                    + "</td></tr>";
        }
        if (ret.isEmpty()) {
            return "<br>Нет данных в черном списке\n";
        }
        return "<br><b>Черный список</b><br><table border=1>"
                + "<tr><th>Предприятие,ОГРН,ИНН</th>"
                + "<th>Ф,И,О,др,Адрес,Паспорт,Примечание</th>"
                + "<th>Код,дата ввода</th></tr>"
                + ret + "</table><br>";
    }

    public String getPasp(String fio, String im, String ot, String dd, String mm, String yyyy) {
        String sql = "select\n"
                + "t.fam||' '||t.imj||' '||t.otch||' '|| -- ФИО\n"
                + "lpad(t.d_rojd,2,'0')||'.'||lpad(t.m_rojd,2,'0')||'.'||t.y_rojd fio_dat_rojd, -- Дата рождения\n"
                + "t.resp_rojd||' '||t.kw_punkt_rojd||' '||t.rajon_rojd||'.' mesto_rojd, -- Место рождения\n"
                + "lpad(t.d_psp,2,'0')||'.'||lpad(t.m_psp,2,'0')||'.'||t.y_psp dat_vyd, -- Дата выдачи паспорта\n"
                + "lpad(t.d_hich,2,'0')||'.'||lpad(t.m_hich,2,'0')||'.'||t.y_hich dat_utr, -- Дата утраты паспорта\n"
                + "t.psp_s||' '||psp_n psp_sn, -- Серия номер\n"
                + "(select text from s2i_klass s2i where s2i.nomer = '031' and s2i.kod = t.PSP_SOST) t_PSP_SOST, -- Состояние\n"
                + "(select text from s2i_klass s2i where s2i.nomer = '371' and s2i.kod = t.PRICH) t_PRICH,          -- Причина\n"
                + "t.adres, -- Прописка\n"
                + "t.organ_psp -- Орган, выдавший паспорт\n"
                + "from t024 t\n"
                + "where t.fam like ? and t.imj like ? and t.otch like ?\n"
                + "and t.d_rojd like ? and t.m_rojd like ? and t.y_rojd like ?"
                + "and rownum < 20";
        String ret = "";
        cl = jdbcTemplate.queryForList(sql,
                new Object[]{fio, im, ot, dd, mm, yyyy});
        //log.info(cl);
        int count = 1;
        for (Map<String, Object> c : cl) {
            ret += "<tr>\n"
                    + "<td rowspan=\"2\">" + (count++) + "</td>\n"
                    + "<td rowspan=\"2\">" + c.get("fio_dat_rojd") + "</td>\n"
                    + "<td>" + c.get("mesto_rojd") + "</td>\n"
                    + "<td>" + c.get("dat_vyd") + "</td>\n"
                    + "<td>" + c.get("dat_utr") + "</td>\n"
                    + "<td>" + c.get("psp_sn") + "</td>\n"
                    + "<td>" + c.get("t_PSP_SOST") + "</td>\n"
                    + "<td>" + c.get("t_PRICH") + "</td>\n"
                    + "</tr>\n"
                    + "<tr>\n"
                    + "<td>" + c.get("adres") + "</td>\n"
                    + "<td colspan=\"5\">" + c.get("organ_psp") + "</td>\n"
                    + "</tr>";
        }
        if (ret.isEmpty()) {
            return "<br>Нет данных по паспортам\n";
        }
        return "<br><b>Данные о паспортах</b><br><table border=1>"
                + "<tr>\n"
                + "<th rowspan=\"2\">№</th>\n"
                + "<th rowspan=\"2\">ФИО дата рождения</th>\n"
                + "<th>Место рождения</th>\n"
                + "<th>Дата выдачи</th>\n"
                + "<th>Дата утраты</th>\n"
                + "<th>Серия, номер паспорта</th>\n"
                + "<th>Состояние</th>\n"
                + "<th>Причина</th>\n"
                + "</tr>\n"
                + "<tr>\n"
                + "<th>Прописка на момент выдачи паспорта</th>\n"
                + "<th colspan=\"5\">Орган, выдавший паспорт</th>\n"
                + "</tr>"
                + ret + "</table><br>";
    }

    public String getAsb(String fio, String im, String ot, String dd, String mm, String yyyy) {
        String sql = "select FAM, NAM, FAT, BORN_D, BORN_M, BORN_Y, ID from ASB_02 where FAM like ? and NAM like ? and FAT like ? and BORN_D like ? and BORN_M like ? and BORN_Y like ? and rownum < 10";
        String ret = "";
        cl = jdbcTemplate.queryForList(sql,
                new Object[]{fio, im, ot, dd, mm, yyyy});
        //log.info(cl);
        AsbSpr asbSpr = new AsbSpr(ds);
        for (Map<String, Object> c : cl) {
            ret += asbSpr.execute(Long.parseLong(c.get("ID").toString()), 1L);
            ret += asbSpr.execute(Long.parseLong(c.get("ID").toString()), 2L);
        }
        if (ret.isEmpty()) {
            return "<br>Нет данных в адресно-справочной картотеке.\n";
        }
        return "<br><b>Адресно-справочная картотека:</b><br>" + ret;
    }

    public class AsbSpr extends StoredProcedure {

        private static final String sql = "asb_spr";

        public AsbSpr(DataSource ds) {
            super(ds, sql);
            declareParameter(new SqlOutParameter("param_out", Types.VARCHAR));
            declareParameter(new SqlParameter("param_in", Types.NUMERIC));
            declareParameter(new SqlParameter("param_in1", Types.NUMERIC));
            setFunction(true);//you must set this as it distinguishes it from a sproc
            compile();
        }

        public String execute(Long rdsId, Long p) {
            String ret = "";
            Map in = new HashMap();
            in.put("param_in", rdsId);
            in.put("param_in1", p);
            Map out = execute(in);
            //log.info(out);
            Object oo = out.get("param_out");
            if (!out.isEmpty() && out != null && oo != null) {
                ret = "\n<pre>" + out.get("param_out").toString() + "<pre>\n";
            }
            return ret;
        }
    }

    public String getDohod(String fio, String im, String ot, String dd, String mm, String yyyy) {
        String sql = "select t.nn, t.god, t.f, t.i, t.o, t.dr,\n"
                + "t.p7, t.p8, t.p9, t.p10, t.adr, t.p12, t.p13, t.pred,\n"
                + "t.d_all, t.d_1, t.d_2, t.d_3, t.d_4, t.d_5, t.d_6, t.d_7, t.d_8, t.d_9, t.d_10, t.d_11, t.d_12\n"
                + "from dohod t\n"
                + "where f like ? and i like ? and o like ? and rownum < 20\n"
                + "order by t.god, t.f, t.i, t.o";
        String ret = "";
        cl = jdbcTemplate.queryForList(sql,
                new Object[]{fio, im, ot});
        //log.info(cl);
        int count = 1;
        for (Map<String, Object> c : cl) {
            ret += "<tr>\n"
                    + "<td>&nbsp;" + (count++) + "</td>\n"
                    + "<td>&nbsp;" + c.get("god") + "</td>\n"
                    + "<td>&nbsp;" + c.get("f") + "</td>\n"
                    + "<td>&nbsp;" + c.get("i") + "</td>\n"
                    + "<td>&nbsp;" + c.get("o") + "</td>\n"
                    + "<td>&nbsp;" + c.get("dr") + "</td>\n"
                    + "<td>&nbsp;" + c.get("adr") + "</td>\n"
                    + "<td>&nbsp;" + c.get("pred") + "</td>\n"
                    + "<td>&nbsp;" + c.get("d_all") + "</td>\n"
                    + "</tr>";
        }
        if (ret.isEmpty()) {
            return "<br>Нет данных о доходах физ.лиц\n";
        }
        return "<br><b>Данные о доходах физ.лиц</b><br><table border=1>"
                + ret + "</table><br>";
    }
    //t.fam, t.imj, t.otch, t.d_rojd
    public String getAmt(String fio, String im, String ot, String dd, String mm, String yyyy) {
        String sql = "select ROWIDTONCHAR(ROWID) id from amts t"
                + " where t.fam like ? and t.imj like ? and t.otch like ? and rownum < 10";
        String sqlAll = "select ROWIDTONCHAR(ROWID) id from amts t"
                + " where t.fam like ? and t.imj like ? and t.otch like ? "
                + " and t.d_rojd like ? and t.m_rojd like ? and t.y_rojd like ?";
        String ret = "";
        if (dd.equals("%") && mm.equals("%") && yyyy.equals("%")) 
            cl = jdbcTemplate.queryForList(sql,new Object[]{fio, im, ot});
        else
            cl = jdbcTemplate.queryForList(sqlAll,new Object[]{fio, im, ot, dd, mm, yyyy});
        //log.info(cl);
        AmtSpr amtSpr = new AmtSpr(ds);
        for (Map<String, Object> c : cl) {
            ret += amtSpr.execute(c.get("ID").toString());
        }
        if (ret.isEmpty()) {
            return "<br>Нет данных в картотеке автотранспорта.\n";
        }
        return "<br><b>Картотека автотранспорта:</b><br>" + ret;
    }

    public class AmtSpr extends StoredProcedure {

        private static final String sql = "amt_spr1";

        public AmtSpr(DataSource ds) {
            super(ds, sql);
            declareParameter(new SqlOutParameter("param_out", Types.VARCHAR));
            declareParameter(new SqlParameter("param_in", Types.VARCHAR));
            setFunction(true);//you must set this as it distinguishes it from a sproc
            compile();
        }

        public String execute(String rdsId) {
            String ret = "";
            Map in = new HashMap();
            in.put("param_in", rdsId);
            Map out = execute(in);
            //log.info(out);
            Object oo = out.get("param_out");
            if (!out.isEmpty() && out != null && oo != null) {
                ret = "\n<pre>" + out.get("param_out").toString() + "<pre>\n";
            }
            return ret;
        }
    }

    public String getGrag(String fio, String im, String ot, String dd, String mm, String yyyy)
            throws FileNotFoundException, UnsupportedEncodingException, IOException {
        String sql = "select g.f, g.i, g.o,\n"
                + "to_number(to_char(g.datar,'DD')) BIRTHD, \n"
                + "to_number(to_char(g.datar,'MM')) BIRTHM, \n"
                + "to_number(to_char(g.datar,'YYYY')) BIRTHY,\n"
                + "replace(h.namefile, '\\', '/') FH\n"
                + "from grag g, dathtml h \n"
                + "where g.tabnom = h.tabnom\n"
                + "and g.f like ? and g.i like ? and g.o like ?\n"
                + "and (\n"
                + "(to_number(to_char(g.datar,'DD')) like ? \n"
                + "and to_number(to_char(g.datar,'MM')) like ?\n"
                + "and to_number(to_char(g.datar,'YYYY')) like ?)\n"
                + "or g.datar is null)\n"
                + "and rownum < 10";
        String fileName = "";
        String ret = "";
        cl = jdbcTemplate.queryForList(sql,
                new Object[]{fio, im, ot, dd, mm, yyyy});
        //log.info(cl);
        for (Map<String, Object> c : cl) {
            fileName = gragPath + c.get("FH");
            //log.info(fileName);
            File f = new File(fileName);
            final int length = (int) f.length();
            if (length != 0) {
                char[] cbuf = new char[length];
                InputStreamReader isr = new InputStreamReader(new FileInputStream(f), "utf-8");
                final int read = isr.read(cbuf);
                String s = new String(cbuf, 0, read);
                ret += s;
                //log.info(s);
                isr.close();
            }
        }

        if (ret.isEmpty()) {
            return "<br>Нет данных в сводных сведениях\n";
        }
        return "<br><b>Сводные сведения</b>"
                + ret + "<br>";
    }
}
