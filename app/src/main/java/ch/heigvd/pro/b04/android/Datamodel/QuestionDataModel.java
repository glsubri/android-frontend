package ch.heigvd.pro.b04.android.Datamodel;

import com.google.gson.annotations.SerializedName;

public class QuestionDataModel {
    @SerializedName("idModerator")
    private String idModerator;

    @SerializedName("idPoll")
    private String idPoll;

    @SerializedName("idQuestion")
    private String idQuestion;

    @SerializedName("title")
    private String title;

    @SerializedName("details")
    private String details;

    @SerializedName("answerMin")
    private String answerMin;

    @SerializedName("answerMax")
    private String answerMax;

    public String getIdModerator() {
        return idModerator;
    }

    public String getIdPoll() {
        return idPoll;
    }

    public String getIdQuestion() {
        return idQuestion;
    }

    public String getTitle() {
        return title;
    }

    public String getDetails() {
        return details;
    }

    public String getAnswerMin() {
        return answerMin;
    }

    public String getAnswerMax() {
        return answerMax;
    }
}
