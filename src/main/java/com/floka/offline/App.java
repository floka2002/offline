package com.floka.offline;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.mail.Address;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.search.FlagTerm;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

public class App {

    private final Log log = LogFactory.getLog(getClass());
    private @Value("${ug.userGmail}")
    String gmailUser;
    private @Value("${ug.userPassword}")
    String gmailPassword;
    private List<Map<String, Object>> cl;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Procedure procedure;

    public static void main(String[] args) {
        ApplicationContext context
                = new ClassPathXmlApplicationContext("app-config.xml");
    }

    public void doMail() {
        String userName = "";
        log.info("doMail");
        Properties props = System.getProperties();
        props.setProperty("mail.store.protocol", "imaps");
        try {
            Session session = Session.getDefaultInstance(props, null);
            Store store = session.getStore("imaps");
            store.connect("imap.gmail.com", gmailUser, gmailPassword);

            Folder inbox = store.getFolder("Inbox");
            inbox.open(Folder.READ_WRITE);
            // Ищем только новые письма
            FlagTerm ft = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
            Message messages[] = inbox.search(ft);
            int i = -1;
            for (Message message : messages) {
                i++;
                // FROM Смотрим и проверяем по базе от кого пришло письмо 
                Address[] a;
                if ((a = message.getFrom()) != null) {
                    // Письмо дб только от одного адресата
                    if (a.length != 1) {
                        //Если письмо не от одного адресата то его удаляем
                        //И пишем первого и второго отправителей в лог
                        log.info("Удалены письма с несколки адресами в поле от:" + a[0].toString() + a[1].toString());
                        message.setFlag(Flags.Flag.DELETED, true);
                        continue;
                    }
                    // Смотрим есть ли данных адрес в БД
                    // Если нет то тоже удаляем
                    cl = jdbcTemplate.queryForList(
                            "select * from offline_users t where ? like '%'||t.user_name||'%'", new Object[]{a[0].toString()});
                    log.info(cl);
                    if (cl.isEmpty()) {
                        log.info("Удаляем сообщение (нет в списке пользователей) от " + a[0].toString());
                        message.setFlag(Flags.Flag.DELETED, true);
                        continue;
                    }
                    userName = cl.get(0).get("user_name").toString();
                    log.info("Обрабатываем сообщение от " + userName);
                }
                String in_query = "";
                if (message.isMimeType("text/plain")) {
                    log.info("Сообщение типа text/plain");
                    in_query = (String) message.getContent();
                } else if (message.isMimeType("multipart/*")) {
                    Multipart mp = (Multipart) message.getContent();
                    Object p = mp.getBodyPart(1).getContent();
                    in_query = p.toString();//object has the body content
                    log.info("Сообщение типа multipart/*");
                }
                //Удаляем все html теги
                in_query = stripTags(in_query.trim());
                String strSubject = message.getSubject();
                log.info(strSubject + " " + in_query);

                // Цикл по ФИО из запроса
                String fio, im, ot, dd, mm, yyyy;
                Pattern pattern = Pattern.compile("([А-Яа-я%]{1,20}?)[\\s]{1,2}([А-Яа-я%]{1,20}?)[\\s]{1,2}([А-Яа-я%]{1,20}?)[\\s]{1,2}([0-9%]{1,2})\\.([0-9%]{1,2})\\.([0-9]{4}|[%])", Pattern.MULTILINE);
                Matcher matcher = pattern.matcher(in_query);
                int fio_count = 0;
                while (matcher.find()) {
                    fio_count++;
                    fio = matcher.group(1).toUpperCase().trim();
                    im = matcher.group(2).toUpperCase().trim();
                    ot = matcher.group(3).toUpperCase().trim();
                    dd = matcher.group(4).trim().replaceFirst("^0", "");
                    mm = matcher.group(5).trim().replaceFirst("^0", "");
                    yyyy = matcher.group(6).trim();

                    String strToSend = "<html><body><b>Ответ за запрос '"
                            + strSubject + "-" + fio_count + "'</b><br>"
                            + fio + " " + im + " " + ot + " " + dd + "." + mm + "." + yyyy;

                    log.info("fio ='" + fio + "'");
                    log.info("im  ='" + im + "'");
                    log.info("ot  ='" + ot + "'");
                    log.info("dd  ='" + dd + "'");
                    log.info("mm  ='" + mm + "'");
                    log.info("yyyy='" + yyyy + "'");

                    //Регистрация запроса
                    jdbcTemplate.update("insert into reg_mail values (?, sysdate, ?,?,?,?,?,?)",
                            userName, fio, im, ot, dd, mm, yyyy);

                    //Выполняем запрос по osk
                    strToSend += procedure.getOsk(fio, im, ot, dd, mm, yyyy);
                    //Выполняем запрос по адмпрактике
                    strToSend += procedure.getAdm(fio, im, ot, dd, mm, yyyy);
                    //Выполняется запрос по черному списку
                    strToSend += procedure.getChs(fio, im, ot, dd, mm, yyyy);
                    //Выполняется запрос по паспортам
                    strToSend += procedure.getPasp(fio, im, ot, dd, mm, yyyy);
                    //Выполняется запрос к адресно-справочной картотеке
                    strToSend += procedure.getAsb(fio, im, ot, dd, mm, yyyy);
                    //Выполняется запрос по доходам физ.лиц
                    strToSend += procedure.getDohod(fio, im, ot, dd, mm, yyyy);
                    //Выполняется запрос по автотранспорту
                    strToSend += procedure.getAmt(fio, im, ot, dd, mm, yyyy);
                    //Выполняется запрос по сводным сведениям
                    strToSend += procedure.getGrag(fio, im, ot, dd, mm, yyyy);

                    strToSend += "</body></html>";

                    //Отправить ответ
                    Message reply = message.reply(false);
                    reply.setContent(strToSend, "text/html;charset=utf8");
                    // отправить его нужно по протоколу SMTPS
                    Transport transport = session.getTransport("smtps");
                    transport.connect("smtp.gmail.com", 465, gmailUser, gmailPassword);
                    transport.sendMessage(reply, reply.getRecipients(Message.RecipientType.TO));
                    transport.close();
                }
                if (fio_count == 0) {
                    String err_query = "\nНеверный формат.\nПри составлении запроса используйте Обычный текст, а не Расширенное форматирование\n"
                            + "Фамилия Имя Отчество чч.мм.гггг";
                    //Отправить сообщение о неверном формате
                    Message reply = message.reply(false);
                    reply.setContent(err_query, "text/html;charset=utf8");
                    // отправить его нужно по протоколу SMTPS
                    Transport transport = session.getTransport("smtps");
                    transport.connect("smtp.gmail.com", 465, gmailUser, gmailPassword);
                    transport.sendMessage(reply, reply.getRecipients(Message.RecipientType.TO));
                    transport.close();

                }
                //Пометить сообщение как прочтенное
                //message.setFlag(Flags.Flag.SEEN, true);
                //Для отладки не помечаем как прочтенное
                //message.setFlag(Flags.Flag.SEEN, false);

            }

            store.close();
            log.info("Соединение с почтой закрыто.");
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public String stripTags(String xmlStr) {
        xmlStr = xmlStr.replaceAll("<.*?>", "");
        //xmlStr = xmlStr.replaceAll("<(\n)+?>", "");
        return xmlStr;
    }
}
