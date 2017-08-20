/**
 * Created by zhilifeng on 8/20/17.
 */
public class RCLink {
    public String type;
    public String source;
    public String dest;
    public String id;

    public RCLink (String type, String source, String dest, String id) {
        this.type = type;
        this.source = source;
        this.dest = dest;
        this.id = id;
    }

    public String toString(){
        return String.format(
                "<RCLINK lid=\"%s\" relType=\"%s\" eventInstanceID=\"%s\" relatedToEventInstance=\"%s\"></RCLINK>",
                this.id,
                this.type,
                this.source,
                this.dest
        );
    }
}
