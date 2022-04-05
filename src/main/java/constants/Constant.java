package constants;

public class Constant {
    private Constant() {}

    //server codination
    public static final String REQUEST_NEW_IDENTITY_APPROVAL = "requestnewidentityapproval";
    public static final String REPLY_NEW_IDENTITY_APPROVAL = "replynewidentityapproval";
    public static final String REQUEST_CREATE_ROOM_APPROVAL = "requestcreateroomapproval";
    public static final String REPLY_CREATE_ROOM_APPROVAL = "replycreateroomapproval";
    public static final String REQUEST_JOIN_ROOM_APPROVAL = "requestjoinroomapproval";
    public static final String REPLY_JOIN_ROOM_APPROVAL = "replyjoinroomapproval";
    public static final String ACK_MOVE_JOIN = "ackmovejoin";
    public static final String REQUEST_LIST = "requestlist";
    public static final String REPLY_LIST = "replylist";
    public static final String REQUEST_DELETE_ROOM = "requestdeleteroom";
    public static final String REQUEST_QUIT = "requestquit";

    // Client communication
    public static final String ROOM_CHANGE = "roomchange";
    public static final String SERVER_CHANGE = "serverchange";
    public static final String NEW_IDENTITY = "newidentity";
    public static final String LIST = "list";
    public static final String ROOMLIST = "roomlist";
    public static final String WHO = "who";
    public static final String ROOM_CONTENTS = "roomcontents";
    public static final String CREATE_ROOM = "createroom";
    public static final String DELETE_ROOM = "deleteroom";
    public static final String JOIN_ROOM = "joinroom";
    public static final String MOVE_JOIN = "movejoin";
    public static final String MESSAGE = "message";
    public static final String ROUTE = "route";
    public static final String QUIT = "quit";

    //heartbeat
    public static final String HEARTBEAT = "heartbeat";
    public static final String REPLY_HEARTBEAT = "replyheartbeat";

    //election
    public static final String TIMER = "Timer";
    public static final String SENDER = "Sender";
    public static final String ELECTION = "election";
    public static final String QUARTZ_JOBS = "quartzjobs";
    public static final String OK = "ok";
    public static final String COORDINATOR = "coordinator";
    public static final String LEADER_UPDATE = "leaderupdate";
    public static final String LEADER_UPDATE_COMPLETE = "leaderupdatecomplete";

}
